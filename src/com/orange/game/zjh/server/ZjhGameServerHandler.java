package com.orange.game.zjh.server;

import org.jboss.netty.channel.MessageEvent;

import com.orange.game.traffic.messagehandler.AbstractMessageHandler;
import com.orange.game.traffic.messagehandler.ChatRequestHandler;
import com.orange.game.traffic.messagehandler.room.CreateRoomRequestHandler;
import com.orange.game.traffic.messagehandler.room.GetRoomRequestHandler;
import com.orange.game.traffic.messagehandler.room.RegisterRoomsRequestHandler;
import com.orange.game.traffic.messagehandler.room.UnRegisterRoomsRequestHandler;
import com.orange.game.traffic.model.dao.GameSession;
import com.orange.game.traffic.model.manager.GameSessionManager;
import com.orange.game.traffic.server.GameEventExecutor;
import com.orange.game.traffic.server.GameServerHandler;
import com.orange.game.zjh.messagehandler.BetRequestHandler;
import com.orange.game.zjh.messagehandler.CheckCardRequestHandler;
import com.orange.game.zjh.messagehandler.CompareCardReuqestHandler;
import com.orange.game.zjh.messagehandler.FoldCardRequestHandler;
import com.orange.game.zjh.messagehandler.ShowCardRequestHandler;
import com.orange.game.zjh.messagehandler.ZjhJoinGameRequestHandler;
import com.orange.game.zjh.messagehandler.ZjhUseItemRequestHandler;
import com.orange.network.game.protocol.message.GameMessageProtos.GameMessage;

public class ZjhGameServerHandler extends GameServerHandler {
	
//	private static final Logger logger = Logger.getLogger(ZjhGameServerHandler.class.getName());
	
	@Override
	public AbstractMessageHandler getMessageHandler(MessageEvent messageEvent) {
		
		GameMessage message = (GameMessage)messageEvent.getMessage();
		
		
		int sessionId = (int) message.getSessionId();
		GameSession session = 
			GameEventExecutor.getInstance().getSessionManager().findSessionById(sessionId);
		// 当session为null，一般是随机加入房间时; 当其不为null, 并且游戏状态不为playing, 忽略不响应。
		if ( session != null && session.getStatus() != GameSession.SessionStatus.PLAYING.ordinal() ) {
			return null;
		}
		
		switch (message.getCommand()){
			case CREATE_ROOM_REQUEST:
				return new CreateRoomRequestHandler(messageEvent);
			
			case GET_ROOMS_REQUEST:
				return new GetRoomRequestHandler(messageEvent);
			
			case CHAT_REQUEST:
				return new ChatRequestHandler(messageEvent);

			case JOIN_GAME_REQUEST:
				return new ZjhJoinGameRequestHandler(messageEvent);
				
			case BET_REQUEST:
				return new BetRequestHandler(messageEvent);
				
			case CHECK_CARD_REQUEST:
				return new CheckCardRequestHandler(messageEvent);
				
			case SHOW_CARD_REQUEST:
				return new ShowCardRequestHandler(messageEvent);
				
			case COMPARE_CARD_REQUEST:
				return new CompareCardReuqestHandler(messageEvent);
				
			case FOLD_CARD_REQUEST:
				return new FoldCardRequestHandler(messageEvent);
				
			case REGISTER_ROOMS_NOTIFICATION_REQUEST:
				return new RegisterRoomsRequestHandler(messageEvent);
				
			case UNREGISTER_ROOMS_NOTIFICATION_REQUEST:
				return new UnRegisterRoomsRequestHandler(messageEvent);
				
			case USE_ITEM_REQUEST:
				return new ZjhUseItemRequestHandler(messageEvent);
				
			default:
				break;
				
		    
		}
		
		return null;
	}

	@Override
	public void userQuitSession(String userId,
			GameSession session, boolean needFireEvent) {		
		GameEventExecutor.getInstance().getSessionManager().userQuitSession(session, userId, needFireEvent, true);								
	}
	
}
