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
import com.orange.network.game.protocol.message.GameMessageProtos.CompareCardRequest;
import com.orange.network.game.protocol.message.GameMessageProtos.CompareCardResponse;
import com.orange.network.game.protocol.message.GameMessageProtos.GameMessage;
import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils.Collections;

public class CompareCardReuqestHandler extends AbstractMessageHandler {

	public CompareCardReuqestHandler(MessageEvent messageEvent) {
		super(messageEvent);
	}

	@Override
	public void handleRequest(GameMessage message, Channel channel,
			GameSession session) {
		
		GameResultCode resultCode;
		String userId = message.getUserId();
		CompareCardRequest request = message.getCompareCardRequest();
		
		if (session == null){
			logger.info("<CompareCardRequestHandler> Session is null !!!");
			resultCode = GameResultCode.ERROR_NO_SESSION_AVAILABLE;
		}
		else if (userId == null){
			logger.info("<CompareCardRequestHandler> UserId is null !!!");
			resultCode = GameResultCode.ERROR_USERID_NULL;
		}
		else {
			// do the real job.
			String toUserId = request.getToUserId();
			resultCode = ((ZjhGameSession)session).compareCard(userId, toUserId); 
		}
		
		
		// Now build the response.
		GameMessage responseMessage;
		GameMessage.Builder builder = GameMessage.newBuilder()
				.setCommand(GameCommandType.COMPARE_CARD_RESPONSE)
				.setMessageId(message.getMessageId())
				.setResultCode(resultCode)
				.setUserId(userId);
		
		if ( resultCode.equals(GameResultCode.SUCCESS) ){
			CompareCardResponse response = CompareCardResponse.newBuilder()
					.addAllUserResult(session.getUserResults())
					.build();
			
			responseMessage = builder.setCompareCardResponse(response).build();
		} else {
			responseMessage = builder.build();
		}
		
		// Send it.
		sendResponse(responseMessage);

		
		if (resultCode == GameResultCode.SUCCESS){
			// Broadcast to all other players.		
			boolean sendToSelf = false;
			GameMessage.Builder brocastBuilder = GameMessageProtos.GameMessage.newBuilder()
					.setCommand(GameCommandType.COMPARE_CARD_REQUEST)
					.setMessageId(GameEventExecutor.getInstance().generateMessageId())
					.setSessionId(session.getSessionId())
					.setUserId(userId)
					.setCompareCardRequest(request); // insert the request to broadcast to all other players.
			
			NotificationUtils.broadcastNotification(session, brocastBuilder, sendToSelf);
			
			// Fire event
			GameEventExecutor.getInstance().fireAndDispatchEvent(GameCommandType.LOCAL_COMPARE_CARD, session.getSessionId(), userId);
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
