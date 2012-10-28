package com.orange.game.zjh.messagehandler;

import java.util.List;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.MessageEvent;

import com.orange.game.traffic.messagehandler.room.JoinGameRequestHandler;
import com.orange.game.traffic.model.dao.GameSession;
import com.orange.game.zjh.model.ZjhGameSession;
import com.orange.network.game.protocol.message.GameMessageProtos.GameMessage;
import com.orange.network.game.protocol.message.GameMessageProtos.JoinGameResponse;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHGameState;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHUserInfo;


public class ZjhJoinGameRequestHandler extends JoinGameRequestHandler {

	
	public ZjhJoinGameRequestHandler(MessageEvent event) {
		super(event);
	}
	
	@Override
	public void handleRequest(GameMessage message, Channel channel, GameSession requestSession) {

		ZjhGameSession session = (ZjhGameSession)processRequest(message, channel, requestSession);
		
		if (session.isGamePlaying()){
//			NotificationUtils.sendUserDiceNotification(session, message.getUserId(), channel, diceNotification);
		}
	}
	
	@Override
	public JoinGameResponse fullfillResponse(JoinGameResponse.Builder builder, GameSession session) {
		
		if (session.isGamePlaying()) {
			int totalBet = ((ZjhGameSession)session).getTotalBet();
			int singleBet =((ZjhGameSession)session).getSingleBet();
			List<PBZJHUserInfo> userInfos = ((ZjhGameSession)session).getUserCardInfo();
			
			PBZJHGameState state = PBZJHGameState.newBuilder()
								.setTotalBet(totalBet)
								.setSingleBet(singleBet)
								.addAllUsersInfo(userInfos)
								.build();
			
			JoinGameResponse response = builder.setZjhGameState(state)
														  .build();
			
			return response;
		   
		} else {	
			return null;
		}
	}
}
