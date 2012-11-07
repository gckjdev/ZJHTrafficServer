package com.orange.game.zjh.messagehandler;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.MessageEvent;

import com.orange.common.log.ServerLog;
import com.orange.game.traffic.messagehandler.AbstractMessageHandler;
import com.orange.game.traffic.model.dao.GameSession;
import com.orange.game.traffic.server.GameEventExecutor;
import com.orange.game.traffic.server.NotificationUtils;
import com.orange.game.zjh.model.ZjhGameSession;
import com.orange.network.game.protocol.constants.GameConstantsProtos.GameCommandType;
import com.orange.network.game.protocol.constants.GameConstantsProtos.GameResultCode;
import com.orange.network.game.protocol.message.GameMessageProtos;
import com.orange.network.game.protocol.message.GameMessageProtos.BetRequest;
import com.orange.network.game.protocol.message.GameMessageProtos.GameMessage;


public class BetRequestHandler extends AbstractMessageHandler {

	
	public BetRequestHandler(MessageEvent messageEvent) {
		super(messageEvent);
	}

	@Override
	public void handleRequest(GameMessage message, Channel channel,
			GameSession session) {
		
		GameResultCode resultCode;
		String userId = message.getUserId();
		BetRequest request = message.getBetRequest();
		
		ServerLog.info(session.getSessionId(), "<betRequest>request is " + request.toString());
		if (session == null){
			logger.info("<BetRequestHandler> Session is null !!!");
			resultCode = GameResultCode.ERROR_NO_SESSION_AVAILABLE;
		}
		else if (userId == null){
			logger.info("<BetRequestHandler> UserId is null !!!");
			resultCode = GameResultCode.ERROR_USERID_NULL;
		}
		else {
			ServerLog.info(session.getSessionId(), "Get bet request from :" + message.getUserId() );
			int singleBet = request.getSingleBet(); // 单注
			int count = request.getCount(); // 注数
			boolean isAutoBet = request.getIsAutoBet(); // 是否自动跟注
			// Do the real job.
			resultCode = ((ZjhGameSession)session).bet(userId, singleBet, count,isAutoBet); 
		}
		
		
		// Now build the response.
		GameMessage response = GameMessage.newBuilder()
				.setCommand(GameCommandType.BET_RESPONSE)
				.setMessageId(message.getMessageId())
				.setResultCode(resultCode)
				.setUserId(userId)
				.setBetRequest(request)
				.build();
		
		// Send it.
		sendResponse(response);

		
		if (resultCode == GameResultCode.SUCCESS){
			// Broadcast to all other players.		
			boolean sendToSelf = false;
			GameMessage.Builder builder = GameMessageProtos.GameMessage.newBuilder()
					.setCommand(GameCommandType.BET_REQUEST)
					.setMessageId(GameEventExecutor.getInstance().generateMessageId())
					.setSessionId(session.getSessionId())
					.setUserId(userId)
					.setBetRequest(request);
			
			NotificationUtils.broadcastNotification(session, builder, sendToSelf);
			
			// Fire event
			GameEventExecutor.getInstance().fireAndDispatchEvent(GameCommandType.LOCAL_BET, session.getSessionId(), userId);
			}
		
	}


	
	@Override
	public boolean isProcessIgnoreSession() {
		return false;
	}

	@Override
	public boolean isProcessInStateMachine() {
		return false;
	}

	@Override
	public boolean isProcessForSessionAllocation() {
		return false;
	}



}
