package com.generallycloud.nio.extend.plugin.jms.client.cmd;

import java.util.HashMap;

import com.generallycloud.nio.common.CloseUtil;
import com.generallycloud.nio.common.cmd.CmdResponse;
import com.generallycloud.nio.common.cmd.CommandContext;
import com.generallycloud.nio.connector.ChannelConnector;

@Deprecated
public class DisconnectExecutable extends MQCommandExecutor {

	public CmdResponse exec(CommandContext context, HashMap<String, String> params) {

		CmdResponse response = new CmdResponse();

		ChannelConnector connector = getClientConnector(context);
		
		if (connector == null) {
			response.setResponse("请先登录！");
			return response;
		}
		
		//FXIME logout
//		connector.logout();
		
		CloseUtil.close(connector);
		
		setMessageBrowser(context, null);
		setClientConnector(context, null);
		
		response.setResponse("已断开连接！");
		return response;
	}
}
