package com.generallycloud.test.nio.base;

import com.generallycloud.nio.codec.base.future.BaseReadFuture;
import com.generallycloud.nio.common.CloseUtil;
import com.generallycloud.nio.connector.SocketChannelConnector;
import com.generallycloud.nio.extend.FixedSession;
import com.generallycloud.nio.extend.IOConnectorUtil;
import com.generallycloud.nio.extend.SimpleIOEventHandle;
import com.generallycloud.nio.extend.implementation.SYSTEMStopServerServlet;

public class TestStopServer {

	public static void main(String[] args) throws Exception {
		String serviceKey = SYSTEMStopServerServlet.SERVICE_NAME;

		SimpleIOEventHandle eventHandle = new SimpleIOEventHandle();

		SocketChannelConnector connector = IOConnectorUtil.getTCPConnector(eventHandle);

		FixedSession session = eventHandle.getFixedSession();

		connector.connect();

		session.login("admin", "admin100");

		BaseReadFuture future = session.request(serviceKey, null);
		System.out.println(future.getText());

		CloseUtil.close(connector);

	}
}
