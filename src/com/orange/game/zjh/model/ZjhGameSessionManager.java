package com.orange.game.zjh.model;

import com.orange.common.log.ServerLog;
import com.orange.game.traffic.model.dao.GameSession;
import com.orange.game.traffic.model.dao.GameUser;
import com.orange.game.traffic.model.manager.GameSessionManager;
import com.orange.network.game.protocol.constants.GameConstantsProtos.GameCommandType;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHRuleType;

public class ZjhGameSessionManager extends GameSessionManager {

	@Override
	public GameSession createSession(int sessionId, String name,
			String password, boolean createByUser, String createBy,
			int ruleType, int maxPlayerCount, int testEnable) {
		return new ZjhGameSession(sessionId, name, password, createByUser, createBy, ruleType, maxPlayerCount, testEnable,
				getInitSingleBet(ruleType), getMaximumBet(ruleType));
	}


	@Override
	public String getGameId() {
		return ZjhGameConstant.GAME_ID_ZJH;
	}

	
	@Override
	public int getRuleType() {
		String ruleType = System.getProperty("ruletype");
		if (ruleType != null && !ruleType.isEmpty()){
			return Integer.parseInt(ruleType);
		}
		return PBZJHRuleType.NORMAL_VALUE;
	}
	
	
   public int getInitSingleBet(int ruleType) {
	   
	    switch (ruleType) {
			case PBZJHRuleType.BEGINER_VALUE:
				return ZjhGameConstant.SINGLE_BET_BEGINER;
			case PBZJHRuleType.NORMAL_VALUE:
				return ZjhGameConstant.SINGLE_BET_NORMAL;
			case PBZJHRuleType.DUAL_VALUE:
				return ZjhGameConstant.SINGLE_BET_DUAL;
			case PBZJHRuleType.RICH_VALUE:
				return ZjhGameConstant.SINGLE_BET_RICH;
			default:
				return 5;
		}
   }
	
   public int getMaximumBet(int ruleType) {
	   
	    switch (ruleType) {
				case PBZJHRuleType.BEGINER_VALUE:
					return ZjhGameConstant.MAX_BET_BEGINER;
				case PBZJHRuleType.NORMAL_VALUE:
					return ZjhGameConstant.MAX_BET_NORMAL;
				case PBZJHRuleType.DUAL_VALUE:
					return ZjhGameConstant.MAX_BET_DUAL;
				case PBZJHRuleType.RICH_VALUE:
					return ZjhGameConstant.MAX_BET_RICH;
				default:
					return 200;
	    }
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
		if (session.isGamePlaying() ) {
			if (session.isCurrentPlayUser(quitUser.getUserId())){
				command = GameCommandType.LOCAL_PLAY_USER_QUIT;			
			}
			else if (aliveUserCount == 1 ){ // 当前存活玩家只剩1个  
				command = GameCommandType.LOCAL_ALL_OTHER_USER_QUIT;			
			}
			else if (quitUser.isPlaying()){ // 旁观者不应该激发事件
				command = GameCommandType.LOCAL_OTHER_USER_QUIT;						
			}	 
			else {
				command = null;
			}
		} else {
			command = null;
		}
		
		return command;
	}

	
	@Override
	public int getMaxPlayerCount() {
		
		int retValue;
		String sessionMaxPlayerCount = System.getProperty("game.maxsessionuser");
		int ruleType = getRuleType();
		
		if ( sessionMaxPlayerCount != null && ! sessionMaxPlayerCount.isEmpty()) {
			retValue = Integer.parseInt(sessionMaxPlayerCount);
		} 
		else if (ruleType == PBZJHRuleType.DUAL_VALUE){
			retValue = 2; 
		} else {
			retValue = ZjhGameConstant.MAX_PLAYER_PER_SESSION;
		}
		
		ServerLog.info(0, "ZjhGameSession : RuleType = "+PBZJHRuleType.valueOf(ruleType)
				+", MaxUserPerSession = "+ retValue);
		return retValue;
	}
}
