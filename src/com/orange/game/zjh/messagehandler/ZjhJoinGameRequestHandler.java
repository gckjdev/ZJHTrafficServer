package com.orange.game.zjh.messagehandler;

import java.util.List;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.MessageEvent;

import com.orange.common.log.ServerLog;
import com.orange.game.traffic.messagehandler.room.JoinGameRequestHandler;
import com.orange.game.traffic.model.dao.GameSession;
import com.orange.game.zjh.model.ZjhGameSession;
import com.orange.network.game.protocol.message.GameMessageProtos.GameMessage;
import com.orange.network.game.protocol.message.GameMessageProtos.JoinGameResponse;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHGameState;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHUserPlayInfo;


public class ZjhJoinGameRequestHandler extends JoinGameRequestHandler {

	
	public ZjhJoinGameRequestHandler(MessageEvent event) {
		super(event);
	}
	
	@Override
	public void handleRequest(GameMessage message, Channel channel, GameSession requestSession) {

		ZjhGameSession session = (ZjhGameSession)processRequest(message, channel, requestSession);

	}
	
	@Override
	public JoinGameResponse responseSpecificPart(JoinGameResponse.Builder builder, GameSession session) {
		
		JoinGameResponse response;
		
		if (session.isGamePlaying()) {
			List<PBZJHUserPlayInfo> userPlayInfos = ((ZjhGameSession)session).getUserPlayInfo();
			int totalBet = ((ZjhGameSession)session).getTotalBet();
			int singleBet =((ZjhGameSession)session).getSingleBet();
			
			PBZJHGameState state = PBZJHGameState.newBuilder()
								.setTotalBet(totalBet)
								.setSingleBet(singleBet)
								.addAllUsersInfo(userPlayInfos)
								.build();
			
			
			response = builder.setZjhGameState(state)
									.build();
			
			ServerLog.info(session.getSessionId(), "!!!!!!!!!!!! JoinGameResponse = " + response);
			
			return response;
		   
		} else {	
			response = builder.build();
			return response;
		}
	}
}
