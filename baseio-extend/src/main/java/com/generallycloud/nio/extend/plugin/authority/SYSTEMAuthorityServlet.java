package com.generallycloud.nio.extend.plugin.authority;

import com.generallycloud.nio.codec.base.future.BaseReadFuture;
import com.generallycloud.nio.component.Session;
import com.generallycloud.nio.extend.ApplicationContext;
import com.generallycloud.nio.extend.ApplicationContextUtil;
import com.generallycloud.nio.extend.LoginCenter;
import com.generallycloud.nio.extend.RESMessage;
import com.generallycloud.nio.extend.security.Authority;
import com.generallycloud.nio.extend.service.BaseFutureAcceptorService;

public class SYSTEMAuthorityServlet extends BaseFutureAcceptorService{
	
	public static final String SERVICE_NAME = SYSTEMAuthorityServlet.class.getSimpleName();

	protected void doAccept(Session session, BaseReadFuture future) throws Exception {
		
		LoginCenter loginCenter = ApplicationContext.getInstance().getLoginCenter();
		
		RESMessage message = RESMessage.UNAUTH;
		
		if (loginCenter.login(session, future.getParameters())) {
			
			Authority authority = ApplicationContextUtil.getAuthority(session);
			
			message = new RESMessage(0, authority,null);
		}
		
		future.write(message.toString());
		
		session.flush(future);
	}
	
}
