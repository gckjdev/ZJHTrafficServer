package com.orange.game.zjh.messagehandler;

import java.util.List;

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
import com.orange.network.game.protocol.message.GameMessageProtos.GameMessage;
import com.orange.network.game.protocol.message.GameMessageProtos.ShowCardRequest;

public class ShowCardRequestHandler extends AbstractMessageHandler {

	public ShowCardRequestHandler(MessageEvent messageEvent) {
		super(messageEvent);
	}
	
	@Override
	public void handleRequest(GameMessage message, Channel channel,
			GameSession session) {

		GameResultCode resultCode;
		String userId = message.getUserId();
		ShowCardRequest request = message.getShowCardRequest();
		
		if (session == null){
			logger.info("<ShowCardRequestHandler> Session is null !!!");
			resultCode = GameResultCode.ERROR_NO_SESSION_AVAILABLE;
		}
		else if (userId == null){
			logger.info("<ShowCardRequestHandler> UserId is null !!!");
			resultCode = GameResultCode.ERROR_USERID_NULL;
		}
		else {
			List<Integer> cardIds = request.getCardIdsList();
			// do the real job.
			resultCode = ((ZjhGameSession)session).showCard(userId, cardIds); 
		}
		
		
		// Now build the response.
		GameMessage response = GameMessage.newBuilder()
				.setCommand(GameCommandType.SHOW_CARD_RESPONSE)
				.setMessageId(message.getMessageId())
				.setResultCode(resultCode)
				.setShowCardRequest(request) 
				.setUserId(userId)
				.build();
		
		// Send it.
		sendResponse(response);

		
		if (resultCode == GameResultCode.SUCCESS){
			// broadcast to all other players.		
			boolean sendToSelf = false;
			GameMessage.Builder builder = GameMessageProtos.GameMessage.newBuilder()
					.setCommand(GameCommandType.SHOW_CARD_REQUEST)
					.setMessageId(GameEventExecutor.getInstance().generateMessageId())
					.setSessionId(session.getSessionId())
					.setUserId(userId)
					.setShowCardRequest(request);
			
			NotificationUtils.broadcastNotification(session, builder, sendToSelf);
			
			// fire event
			GameEventExecutor.getInstance().fireAndDispatchEvent(GameCommandType.LOCAL_SHOW_CARD, session.getSessionId(), userId);
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
