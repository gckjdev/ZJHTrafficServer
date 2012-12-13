package com.orange.game.zjh.robot.client;

import com.orange.game.model.dao.User;
import com.orange.game.traffic.robot.client.AbstractRobotClient;
import com.orange.game.traffic.robot.client.AbstractRobotManager;

public class ZjhRobotManager extends AbstractRobotManager {

	public ZjhRobotManager() {
		super();
	}
	
	@Override
	public AbstractRobotClient createRobotClient(User robotUser, int sessionId,
			int index) {
		return new ZjhRobotClient(robotUser, sessionId, index);
	}
}
