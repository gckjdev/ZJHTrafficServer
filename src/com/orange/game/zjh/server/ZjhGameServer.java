package com.orange.game.zjh.server;

import com.orange.common.statemachine.StateMachine;
import com.orange.game.traffic.robot.client.AbstractRobotManager;
import com.orange.game.traffic.robot.client.RobotService;
import com.orange.game.traffic.server.GameServer;
import com.orange.game.zjh.model.ZjhGameSessionManager;
import com.orange.game.zjh.robot.client.ZjhRobotManager;
import com.orange.game.zjh.statemachine.ZjhGameStateMachineBuilder;

public class ZjhGameServer {
	
	public static void main(String[] args) {

		final AbstractRobotManager robotManager = new ZjhRobotManager();
		RobotService.getInstance().initRobotManager(robotManager);
		
		// init data
		StateMachine ZjhStateMachine = ZjhGameStateMachineBuilder.getInstance().buildStateMachine();
		ZjhGameSessionManager sessionManager = new ZjhGameSessionManager();
		
		// create server
		GameServer server = new GameServer(new ZjhGameServerHandler(), ZjhStateMachine, sessionManager, robotManager);
		
		// start server
		server.start();
		
	}
}
