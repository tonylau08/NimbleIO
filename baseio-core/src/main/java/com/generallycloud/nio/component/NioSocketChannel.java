package com.generallycloud.nio.component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.RejectedExecutionException;

import com.generallycloud.nio.common.CloseUtil;
import com.generallycloud.nio.common.ReleaseUtil;
import com.generallycloud.nio.component.ChannelFlusher.ChannelFlusherEvent;
import com.generallycloud.nio.component.concurrent.ListQueue;
import com.generallycloud.nio.component.concurrent.ListQueueLink;
import com.generallycloud.nio.protocol.IOReadFuture;
import com.generallycloud.nio.protocol.IOWriteFuture;
import com.generallycloud.nio.protocol.ProtocolDecoder;
import com.generallycloud.nio.protocol.ProtocolEncoder;
import com.generallycloud.nio.protocol.ProtocolFactory;

public class NioSocketChannel extends AbstractChannel implements com.generallycloud.nio.component.SocketChannel {

	private Socket					socket;
	private SocketChannel			channel;
	private IOSession				session;
	private IOReadFuture			readFuture;
	private SelectionKey			selectionKey;
	private ChannelFlusher			channelFlusher;
	private boolean				networkWeak;
	private ProtocolDecoder			protocolDecoder;
	private ProtocolEncoder			protocolEncoder;
	private ProtocolFactory			protocolFactory;
	private IOWriteFuture			writeFuture;
	private boolean				opened			= true;
	private long					next_network_weak	= Long.MAX_VALUE;
	
	//FIXME 这里最好不要用ABQ，使用链式可增可减
	private ListQueue<IOWriteFuture>	writeFutures			= new ListQueueLink<IOWriteFuture>();
//	private ListQueue<IOWriteFuture>	writeFutures			= new ListQueueABQ<IOWriteFuture>(1024 * 10);

	// FIXME 改进network wake 机制
	// FIXME network weak check
	public NioSocketChannel(BaseContext context, SelectionKey selectionKey, ChannelFlusher channelFlusher)
			throws SocketException {
		super(context);
		this.selectionKey = selectionKey;
		this.channelFlusher = channelFlusher;
		this.channel = (SocketChannel) selectionKey.channel();
		this.socket = channel.socket();
		this.local = getLocalSocketAddress();
		if (socket == null) {
			throw new SocketException("socket is empty");
		}

		this.session = new IOSessionImpl(this, getChannelID());
	}

	public void close() throws IOException {
		CloseUtil.close(session);
	}

	public boolean flush() throws IOException {

		if (writeFuture == null) {
			writeFuture = writeFutures.poll();
		}

		if (writeFuture == null) {
			return true;
		}

		if (!writeFuture.write(this)) {
			return false;
		}

		writeFuture.onSuccess(session);

		writeFuture = null;

		return true;
	}

	public IOWriteFuture getWriteFuture() {
		return writeFuture;
	}

	public InetSocketAddress getLocalSocketAddress() {
		if (local == null) {
			local = (InetSocketAddress) socket.getLocalSocketAddress();
		}
		return local;
	}

	protected String getMarkPrefix() {
		return "TCP";
	}

	public int getMaxIdleTime() throws SocketException {
		return socket.getSoTimeout();
	}

	public ProtocolDecoder getProtocolDecoder() {
		return protocolDecoder;
	}

	public ProtocolEncoder getProtocolEncoder() {
		return protocolEncoder;
	}

	public ProtocolFactory getProtocolFactory() {
		return protocolFactory;
	}

	public IOReadFuture getReadFuture() {
		return readFuture;
	}

	public InetSocketAddress getRemoteSocketAddress() {
		if (remote == null) {
			remote = (InetSocketAddress) socket.getRemoteSocketAddress();
		}
		return remote;
	}

	public IOSession getSession() {
		return session;
	}

	public int getWriteFutureSize() {
		return writeFutures.size();
	}

	private void interestWrite() {
		selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
	}

	public boolean isBlocking() {
		return channel.isBlocking();
	}

	public boolean isNetworkWeak() {
		return networkWeak;
	}

	// FIXME 是否使用channel.isOpen()
	public boolean isOpened() {
		return opened;
	}

	public void offer(IOWriteFuture future) {

		if (!isOpened()) {
			
			future.onException(session,new ClosedChannelException());
			
			return;
		}
		
		if(!writeFutures.offer(future)){
			
			future.onException(session,new RejectedExecutionException());
			
			return;
		}

		channelFlusher.offer(this);
	}
	
	
	private void releaseWriteFutures(){
		
		ReleaseUtil.release(writeFuture);
		
		ListQueue<IOWriteFuture> writeFutures = this.writeFutures;
		
		IOWriteFuture f = writeFutures.poll();
		
		for(; f != null;){
			
			ReleaseUtil.release(f);
			
			f = writeFutures.poll();
		}
		
	}
	
	public void physicalClose() throws IOException {
		
		this.opened = false;
		
		this.releaseWriteFutures();

		this.selectionKey.attach(null);

		this.channel.close();
	}

	public int read(ByteBuffer buffer) throws IOException {
		return this.channel.read(buffer);
	}

	public void setWriteFuture(IOWriteFuture future) {
		this.writeFuture = future;
	}

	public void setProtocolDecoder(ProtocolDecoder protocolDecoder) {
		this.protocolDecoder = protocolDecoder;
	}

	public void setProtocolEncoder(ProtocolEncoder protocolEncoder) {
		this.protocolEncoder = protocolEncoder;
	}

	public void setProtocolFactory(ProtocolFactory protocolFactory) {
		this.protocolFactory = protocolFactory;
	}

	public void setReadFuture(IOReadFuture readFuture) {
		this.readFuture = readFuture;
	}
	
	public void upNetworkState() {
		
		if (next_network_weak != Long.MAX_VALUE) {

			next_network_weak = Long.MAX_VALUE;

			networkWeak = false;
		}
		
	}

	public void downNetworkState() {
		
		if (next_network_weak < Long.MAX_VALUE) {

			if (System.currentTimeMillis() > next_network_weak) {

				if (!networkWeak) {

					networkWeak = true;

					interestWrite();
				}
			}
			
		} else {

			next_network_weak = System.currentTimeMillis() + 64;
		}
	}

	public void updateNetworkState(int length) {

		if (length == 0) {
			if (next_network_weak < Long.MAX_VALUE) {

				if (System.currentTimeMillis() > next_network_weak) {

					if (!networkWeak) {

						networkWeak = true;

						interestWrite();
					}
				}
			} else {

				next_network_weak = System.currentTimeMillis() + 64;
			}
		} else {

			if (next_network_weak != Long.MAX_VALUE) {

				next_network_weak = Long.MAX_VALUE;

				networkWeak = false;
			}
		}
	}

	public void wakeup() throws IOException {

		this.channelFlusher.fire(new ChannelFlusherEvent() {

			public void handle(ChannelFlusher flusher) {

				NioSocketChannel channel = NioSocketChannel.this;

				channel.updateNetworkState(1);

				flusher.wekeupSocketChannel(channel);
			}
		});

		this.selectionKey.interestOps(SelectionKey.OP_READ);
	}

	public int write(ByteBuffer buffer) throws IOException {
		return channel.write(buffer);
	}
}
