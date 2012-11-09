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
	public String getGameId() {
		return ZjhGameConstant.GAME_ID_ZJH;
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

	@Override
	public boolean takeOverWhenUserQuit(GameSession session, GameUser quitUser,
			int sessionUserCount) {
		return false;
	}

	@Override
	public void updateQuitUserInfo(GameSession session, GameUser quitUser) {
		// 断开后，更新ZjhGameSession中关于该玩家的一些信息
		((ZjhGameSession)session).updateQuitPlayerInfo(quitUser.getUserId());	
	}

	@Override
	public GameCommandType  getCommandForUserQuitSession(GameSession session, GameUser quitUser, int sessionUserCount){
		int aliveUserCount = ((ZjhGameSession)session).getAlivePlayerCount();
		GameCommandType command = null;		
		if (session.isCurrentPlayUser(quitUser.getUserId())){
			command = GameCommandType.LOCAL_PLAY_USER_QUIT;			
		}
		else if (aliveUserCount == 1 ){ // 当前存活玩家只剩1个  
			command = GameCommandType.LOCAL_ALL_OTHER_USER_QUIT;			
		}
		else {
			command = GameCommandType.LOCAL_OTHER_USER_QUIT;						
		}	
		
		return command;
	}
}
