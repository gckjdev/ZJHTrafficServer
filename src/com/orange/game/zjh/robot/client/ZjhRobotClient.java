package com.orange.game.zjh.robot.client;

import com.orange.common.mongodb.MongoDBClient;
import com.orange.game.model.dao.User;
import com.orange.game.traffic.robot.client.AbstractRobotClient;


public class ZjhRobotClient extends AbstractRobotClient {

//	private final static Logger logger = Logger.getLogger(ZjhRobotClient.class.getName());
	
	public ZjhRobotClient(User user, int sessionId, int index) {
		super(user, sessionId, index);
		// TODO Auto-generated constructor stub
	}

	@Override
	public int calNewLevel(long experience) {
		// TODO Auto-generated method stub
		return 0;
	}
	@Override
	public String getAppId() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public MongoDBClient getMongoDBClient() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long incExperience() {
		// TODO Auto-generated method stub
		return 0;
	}
	@Override
	public void chargeBalance() {
		// TODO Auto-generated method stub
		
	}
	

}
