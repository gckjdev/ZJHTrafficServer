package com.orange.game.zjh.messagehandler;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.MessageEvent;

import com.orange.game.traffic.messagehandler.AbstractMessageHandler;
import com.orange.game.traffic.model.dao.GameSession;
import com.orange.network.game.protocol.message.GameMessageProtos.GameMessage;


public class BetRequestHandler extends AbstractMessageHandler {

	
	public BetRequestHandler(MessageEvent messageEvent) {
		super(messageEvent);
	}

	@Override
	public void handleRequest(GameMessage message, Channel channel,
			GameSession session) {

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
