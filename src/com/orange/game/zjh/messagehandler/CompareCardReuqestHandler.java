package com.orange.game.zjh.messagehandler;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.MessageEvent;

import com.orange.game.traffic.messagehandler.AbstractMessageHandler;
import com.orange.game.traffic.model.dao.GameSession;
import com.orange.network.game.protocol.message.GameMessageProtos.GameMessage;

public class CompareCardReuqestHandler extends AbstractMessageHandler {

	public CompareCardReuqestHandler(MessageEvent messageEvent) {
		super(messageEvent);
	}

	@Override
	public void handleRequest(GameMessage message, Channel channel,
			GameSession session) {
		
		if (session == null){
			return;
		}

		String userId = message.getUserId();
		if (userId == null){
			return;
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
