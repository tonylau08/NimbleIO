package com.generallycloud.nio.component;

import com.generallycloud.nio.AbstractLifeCycle;
import com.generallycloud.nio.LifeCycle;
import com.generallycloud.nio.common.Logger;
import com.generallycloud.nio.common.LoggerFactory;
import com.generallycloud.nio.protocol.ReadFuture;

public abstract class IOEventHandleAdaptor extends AbstractLifeCycle implements IOEventHandle, LifeCycle {

	private Logger		logger	= LoggerFactory.getLogger(IOEventHandleAdaptor.class);

	public void exceptionCaught(Session session, ReadFuture future, Exception cause, IOEventState state) {
		logger.error(cause.getMessage(),cause);
	}

	public void futureSent(Session session, ReadFuture future) {
		
	}

	protected void doStart() throws Exception {

	}

	protected void doStop() throws Exception {

	}

}
