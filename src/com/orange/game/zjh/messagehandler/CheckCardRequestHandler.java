package com.orange.game.zjh.messagehandler;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.MessageEvent;

import com.orange.game.traffic.messagehandler.AbstractMessageHandler;
import com.orange.game.traffic.model.dao.GameSession;
import com.orange.game.traffic.server.GameEventExecutor;
import com.orange.game.traffic.server.NotificationUtils;
import com.orange.game.zjh.model.ZjhGameSession;
import com.orange.network.game.protocol.constants.GameConstantsProtos.GameCommandType;
import com.orange.network.game.protocol.constants.GameConstantsProtos.GameResultCode;
import com.orange.network.game.protocol.message.GameMessageProtos;
import com.orange.network.game.protocol.message.GameMessageProtos.CheckCardRequest;
import com.orange.network.game.protocol.message.GameMessageProtos.GameMessage;

public class CheckCardRequestHandler extends AbstractMessageHandler {

	public CheckCardRequestHandler(MessageEvent messageEvent) {
		super(messageEvent);
	}

	@Override
	public void handleRequest(GameMessage message, Channel channel,
			GameSession session) {
		
		GameResultCode resultCode;
		String userId = message.getUserId();
		CheckCardRequest request = message.getCheckCardRequest();
		
		if (session == null){
			logger.info("<CheckCardRequestHandler> Session is null !!!");
			resultCode = GameResultCode.ERROR_NO_SESSION_AVAILABLE;
		}
		else if (userId == null){
			logger.info("<CheckCardRequestHandler> UserId is null !!!");
			resultCode = GameResultCode.ERROR_USERID_NULL;
		}
		else {
			// do the real job.
			resultCode = ((ZjhGameSession)session).checkCard(userId); 
		}
		
		
		// Now build the response.
		GameMessage response = GameMessage.newBuilder()
				.setCommand(GameCommandType.CHECK_CARD_RESPONSE)
				.setMessageId(message.getMessageId())
				.setResultCode(resultCode)
				.setUserId(userId)
				.build();
		
		// Send it.
		sendResponse(response);

		
		if (resultCode == GameResultCode.SUCCESS){
			// Broadcast to all other players.		
			boolean sendToSelf = false;
			GameMessage.Builder builder = GameMessageProtos.GameMessage.newBuilder()
					.setCommand(GameCommandType.CHECK_CARD_REQUEST)
					.setMessageId(GameEventExecutor.getInstance().generateMessageId())
					.setSessionId(session.getSessionId())
					.setUserId(userId)
					.setCheckCardRequest(request);
			
			NotificationUtils.broadcastNotification(session, builder, sendToSelf);
			
			// Player can check card anytime, so need to check is it my turn to
			// decide whether to fire the event to make the state machine transit
			if ( session.getCurrentPlayUserId().equals(userId)) { 
				// Fire event
				GameEventExecutor.getInstance().fireAndDispatchEvent(GameCommandType.LOCAL_CHECK_CARD, session.getSessionId(), userId);
			}
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
