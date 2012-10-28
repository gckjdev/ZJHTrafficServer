package com.orange.game.zjh.server;

import org.jboss.netty.channel.MessageEvent;
import com.orange.game.traffic.messagehandler.AbstractMessageHandler;
import com.orange.game.traffic.messagehandler.ChatRequestHandler;
import com.orange.game.traffic.messagehandler.room.CreateRoomRequestHandler;
import com.orange.game.traffic.messagehandler.room.GetRoomRequestHandler;
import com.orange.game.traffic.model.dao.GameSession;
import com.orange.game.traffic.server.GameEventExecutor;
import com.orange.game.traffic.server.GameServerHandler;
import com.orange.game.zjh.messagehandler.ZjhJoinGameRequestHandler;
import com.orange.network.game.protocol.message.GameMessageProtos.GameMessage;

public class ZjhGameServerHandler extends GameServerHandler {
	
//	private static final Logger logger = Logger.getLogger(ZjhGameServerHandler.class.getName());
	
	@Override
	public AbstractMessageHandler getMessageHandler(MessageEvent messageEvent) {
		
		GameMessage message = (GameMessage)messageEvent.getMessage();
		
		switch (message.getCommand()){
			case CREATE_ROOM_REQUEST:
				return new CreateRoomRequestHandler(messageEvent);
			
			case GET_ROOMS_REQUEST:
				return new GetRoomRequestHandler(messageEvent);
			
			case CHAT_REQUEST:
				return new ChatRequestHandler(messageEvent);

			case JOIN_GAME_REQUEST:
				return new ZjhJoinGameRequestHandler(messageEvent);
				
			default:
				break;
				
		    
		}
		
		return null;
	}

	@Override
	public void userQuitSession(String userId,
			GameSession session, boolean needFireEvent) {
		
		GameEventExecutor.getInstance().getSessionManager().userQuitSession(session, userId, needFireEvent);
				
		
		
	}
	
}
