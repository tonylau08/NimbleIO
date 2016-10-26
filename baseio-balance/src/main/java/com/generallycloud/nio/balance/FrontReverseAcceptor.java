package com.generallycloud.nio.balance;

import java.io.IOException;

import com.generallycloud.nio.acceptor.SocketChannelAcceptor;
import com.generallycloud.nio.common.LoggerFactory;
import com.generallycloud.nio.common.LoggerUtil;
import com.generallycloud.nio.component.BaseContext;

public class FrontReverseAcceptor {

	private SocketChannelAcceptor		acceptor	= new SocketChannelAcceptor();

	protected void start(BaseContext context) throws IOException {

		this.acceptor.setContext(context);

		this.acceptor.bind();

		LoggerUtil.prettyNIOServerLog(LoggerFactory.getLogger(FrontReverseAcceptor.class),
				"Front Reverse Acceptor 启动成功 ...");
	}
	
	protected SocketChannelAcceptor getAcceptor() {
		return acceptor;
	}

	protected void stop() {
		acceptor.unbind();
	}
}
