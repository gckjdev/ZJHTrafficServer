package com.orange.game.zjh.model;

import com.orange.common.log.ServerLog;
import com.orange.game.traffic.model.dao.GameSession;
import com.orange.game.traffic.model.dao.GameUser;
import com.orange.game.traffic.model.manager.GameSessionManager;
import com.orange.game.traffic.server.GameEventExecutor;
import com.orange.game.traffic.server.NotificationUtils;
import com.orange.game.traffic.service.SessionUserService;
import com.orange.network.game.protocol.constants.GameConstantsProtos.GameCommandType;

public class ZjhGameSessionManager extends GameSessionManager {

	@Override
	public GameSession createSession(int sessionId, String name, String password, boolean createByUser, String createBy, int ruleType,int testEnable) {
		return new ZjhGameSession(sessionId, name, password, createByUser, createBy, ruleType,testEnable);
	}

	@Override
	public void userQuitSession(GameSession session, String userId, boolean needFireEvent) {
		int sessionId = session.getSessionId();
		ServerLog.info(sessionId, "user "+userId+" quit");

		int sessionUserCount = session.getUserCount();
		GameUser user = session.findUser(userId);
		if (user == null){
			ServerLog.info(sessionId, "user "+userId+" quit, but user not found in session");
			return;
		}
				
		boolean removeUser = true;
		
		if (!removeUser){
			session.takeOverUser(userId);
		}
		
		GameCommandType command = null;		
		if (session.isCurrentPlayUser(userId)){
			command = GameCommandType.LOCAL_PLAY_USER_QUIT;			
		}
		else if (sessionUserCount <= 2){
			command = GameCommandType.LOCAL_ALL_OTHER_USER_QUIT;			
		}
		else {
			command = GameCommandType.LOCAL_OTHER_USER_QUIT;						
		}			
		
		// 在断开连接前，要先更新该玩家的一些信息，以免断开连接后找不到userId
		((ZjhGameSession)session).updateQuitPlayerInfo(userId);
		
		// broadcast user exit message to all other users
		NotificationUtils.broadcastUserStatusChangeNotification(session, userId);			
		
		// fire message
		if (needFireEvent){
			GameEventExecutor.getInstance().fireAndDispatchEvent(command, sessionId, userId);
		}
		
		if (removeUser){
			SessionUserService.getInstance().removeUser(session, userId);
		}
	}

	@Override
	public String getGameId() {
		return ZjhGameConstant.ZJH_GAME_ID;
	}

	
	@Override
	public int getRuleType() {
//		String ruleType = System.getProperty("ruletype");
//		if (ruleType != null && !ruleType.isEmpty()){
//			return Integer.parseInt(ruleType);
//		}
//		return DiceGameRuleType.RULE_NORMAL_VALUE; // default
		return 0;
	}
	

	
	@Override
	// On: 1, Off:0[default]
	public int getTestEnable() {
			String testEnable = System.getProperty("test_enable");
			if (testEnable != null && !testEnable.isEmpty()){
				return Integer.parseInt(testEnable);
			}
			return 0;
	}


	
}
