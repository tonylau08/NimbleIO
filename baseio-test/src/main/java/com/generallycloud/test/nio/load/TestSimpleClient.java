package com.generallycloud.test.nio.load;

import com.generallycloud.nio.codec.base.BaseProtocolFactory;
import com.generallycloud.nio.common.CloseUtil;
import com.generallycloud.nio.common.SharedBundle;
import com.generallycloud.nio.common.ThreadUtil;
import com.generallycloud.nio.component.IOEventHandleAdaptor;
import com.generallycloud.nio.component.Session;
import com.generallycloud.nio.connector.SocketChannelConnector;
import com.generallycloud.nio.extend.IOConnectorUtil;
import com.generallycloud.nio.protocol.ReadFuture;
import com.generallycloud.test.nio.common.ReadFutureFactory;

public class TestSimpleClient {

	public static void main(String[] args) throws Exception {

		SharedBundle.instance().loadAllProperties("nio");

		IOEventHandleAdaptor eventHandleAdaptor = new IOEventHandleAdaptor() {

			public void accept(Session session, ReadFuture future) throws Exception {
				System.out.println(future);
			}
		};

		SocketChannelConnector connector = IOConnectorUtil.getTCPConnector(eventHandleAdaptor);

		connector.getContext().setProtocolFactory(new BaseProtocolFactory());
		
		connector.connect();

		Session session = connector.getSession();

		ReadFuture future = ReadFutureFactory.create(session, "test", session.getContext().getIOEventHandleAdaptor());

		future.write("hello server !");

		session.flush(future);
		
		ThreadUtil.sleep(500);

		CloseUtil.close(connector);

	}
}
