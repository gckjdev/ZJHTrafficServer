package com.orange.game.zjh.robot.client;

import org.apache.log4j.Logger;

import com.orange.game.traffic.server.GameEventExecutor;
import com.orange.game.zjh.model.ZjhGameSession;
import com.orange.network.game.protocol.message.GameMessageProtos.GameStartNotificationRequest;

public class ZjhRobotIntelligence {
	
	private static final Logger logger = Logger.getLogger(ZjhRobotIntelligence.class.getName()) ;
	
	private int sessionid;
	private ZjhGameSession session;
	
	public ZjhRobotIntelligence(int sessionId) {
		this.sessionid = sessionId;
		this.session = (ZjhGameSession) GameEventExecutor.getInstance().getSessionManager().findSessionById(sessionId);
	}


	public void introspectPokers(GameStartNotificationRequest gameStartNotificationRequest) {
		
	}
	
	

}