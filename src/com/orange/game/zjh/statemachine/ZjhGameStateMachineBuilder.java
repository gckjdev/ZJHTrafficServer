package com.orange.game.zjh.statemachine;


import com.orange.common.statemachine.Action;
import com.orange.common.statemachine.Condition;
import com.orange.common.statemachine.DecisionPoint;
import com.orange.common.statemachine.State;
import com.orange.common.statemachine.StateMachine;
import com.orange.common.statemachine.StateMachineBuilder;
import com.orange.game.traffic.model.dao.GameUser;
import com.orange.game.traffic.statemachine.CommonGameAction;
import com.orange.game.traffic.statemachine.CommonGameCondition;
import com.orange.game.traffic.statemachine.CommonGameState;
import com.orange.game.zjh.model.ZjhGameSession;
import com.orange.game.zjh.statemachine.action.ZjhGameAction;
import com.orange.game.zjh.statemachine.state.GameState;
import com.orange.game.zjh.statemachine.state.GameStateKey;
import com.orange.network.game.protocol.constants.GameConstantsProtos.GameCommandType;

public class ZjhGameStateMachineBuilder extends StateMachineBuilder {

	// thread-safe singleton implementation
    private static ZjhGameStateMachineBuilder builder = new ZjhGameStateMachineBuilder();
    
    private ZjhGameStateMachineBuilder(){		
	 } 	
    public static ZjhGameStateMachineBuilder getInstance() {         	
    	return builder; 
     }
	
    public static final State INIT_STATE = new CommonGameState(GameStateKey.CREATE);

    
    private static final int START_GAME_TIMEOUT = 3; // 用户就绪到游戏开始的等待时间
    private static final int WAIT_CLAIM_TIMEOUT = 17;// 每个玩家当前轮次等待时间

    
	@Override
	public StateMachine buildStateMachine() {
		
		StateMachine stateMachine = new StateMachine();
		
		
		Action initGame        		    = new CommonGameAction.InitGame();
		Action clearTimer		 			 = new CommonGameAction.ClearTimer();
		Action prepareRobot				 = new CommonGameAction.PrepareRobot();
		Action clearRobotTimer			 = new CommonGameAction.ClearRobotTimer();
		Action selectPlayer 			 	 = new CommonGameAction.SelectPlayUser();
		Action startPlayGame 			 = new CommonGameAction.StartGame();
		Action finishPlayGame			 = new CommonGameAction.FinishGame();
		Action setStartGameTimer 		 = new CommonGameAction.CommonTimer(START_GAME_TIMEOUT, ZjhGameAction.ZjhTimerType.START_GAME);
		Action setWaitClaimTimer		 = new CommonGameAction.CommonTimer(WAIT_CLAIM_TIMEOUT, ZjhGameAction.ZjhTimerType.WAIT_CLAIM);
		Action kickPlayUser     		 = new CommonGameAction.KickPlayUser(); 
		Action setOneUserWaitTimer		 = new CommonGameAction.SetOneUserWaitTimer();
		Action notifyGameStartAndDealTimer 
												 = new ZjhGameAction.NotifyGameStartAndDealTimer();
		Action notifyGameStartAndDeal  = new ZjhGameAction.NotifyGameStartAndDeal(); 
		Action broadcastNextPlayerNotification
												 = new ZjhGameAction.BroadcastNextPlayerNotification();
		Action completeGame				 = new ZjhGameAction.CompleteGame();
		Action setShowResultTimer		 = new ZjhGameAction.SetShowResultTimer();
		Action clearPlayingStatus		 = new ZjhGameAction.ClearAllPlayingStatus();
		Action restartGame				 = new ZjhGameAction.RestartGame();	
		Action setAlivePlayerCout		 = new ZjhGameAction.SetAlivePlayerCout();
		Action autoFoldCard            = new ZjhGameAction.AutoFoldCard();
		Action setTotalBet				 = new ZjhGameAction.SetTotalBet();
		Action setAllPlayerLoseGameToFalse
												 = new ZjhGameAction.SetAllPlayerLoseGameToFalse();
		Action updateQuitPlayerInfo	 = new ZjhGameAction.UpdateQuitPlayerInfo();
		
		Condition checkUserCount = new CommonGameCondition.CheckUserCount();
		 
		stateMachine.addState(INIT_STATE)
						.addAction(initGame)
						.addAction(clearTimer)
						.addTransition(GameCommandType.LOCAL_NEW_USER_JOIN, GameStateKey.CHECK_USER_COUNT);
		
		stateMachine.addState(new GameState(GameStateKey.CHECK_USER_COUNT))
						.setDecisionPoint(new DecisionPoint(checkUserCount) {
								@Override
								public Object decideNextState(Object context){
									int userCount = condition.decide(context);
									if (userCount == 0){
										return GameStateKey.CREATE;
									}
									else if (userCount <= 1){
										return GameStateKey.ONE_USER_WAITING;
									}
									else{ // more than one user, can start game
										return GameStateKey.WAIT_FOR_START_GAME;
									}
								}
						});
		
		
		
		stateMachine.addState(new GameState(GameStateKey.ONE_USER_WAITING))
						.addAction(setOneUserWaitTimer)
						.addAction(prepareRobot)
						.addTransition(GameCommandType.LOCAL_NEW_USER_JOIN, GameStateKey.CHECK_USER_COUNT)
						.addTransition(GameCommandType.LOCAL_USER_QUIT, GameStateKey.CHECK_USER_COUNT)
						.addAction(clearTimer)
						.addAction(clearRobotTimer);				
		
		
		
		stateMachine.addState(new GameState(GameStateKey.WAIT_FOR_START_GAME))
						.addAction(setStartGameTimer)
						.addTransition(GameCommandType.LOCAL_USER_QUIT, GameStateKey.PLAY_USER_QUIT)
						.addTransition(GameCommandType.LOCAL_TIME_OUT, GameStateKey.DEAL)				
						.addAction(clearTimer);
		
		
		
		stateMachine.addState(new GameState(GameStateKey.DEAL))
						.addAction(startPlayGame)
						.addAction(setAlivePlayerCout)
						.addAction(setTotalBet)
						.addAction(setAllPlayerLoseGameToFalse)
						.addAction(notifyGameStartAndDealTimer)
						.addAction(notifyGameStartAndDeal)
						.addTransition(GameCommandType.LOCAL_PLAY_USER_QUIT, GameStateKey.PLAY_USER_QUIT)  // current playing user quit
						.addTransition(GameCommandType.LOCAL_ALL_OTHER_USER_QUIT,GameStateKey.CREATE)
						.addTransition(GameCommandType.LOCAL_OTHER_USER_QUIT, GameStateKey.CHECK_USER_COUNT)
						.addTransition(GameCommandType.LOCAL_TIME_OUT, GameStateKey.SELECT_NEXT_PLAYER)
						.addEmptyTransition(GameCommandType.LOCAL_NEW_USER_JOIN)  // 旁观者加入
						.addAction(clearTimer);
						
		
		
		stateMachine.addState(new GameState(GameStateKey.SELECT_NEXT_PLAYER))
						.addAction(clearTimer) // clear last player's timer
						.addAction(selectPlayer)
						.setDecisionPoint(new DecisionPoint(null) {
							@Override
							public Object decideNextState(Object context){
								ZjhGameSession session = (ZjhGameSession)context;
								int alivePlayerCount = session.getAlivePlayerCount();
								if ( alivePlayerCount <= 1 )
									return GameStateKey.COMPLETE_GAME;
								else {
									GameUser user = session.getCurrentPlayUser();
									if (session.getUserCount() <= 1){
										return GameStateKey.CHECK_USER_COUNT;
									}
									if (user != null ){
										return GameStateKey.WAIT_NEXT_PLAYER_PLAY;
									}
									else{
										return GameStateKey.SELECT_NEXT_PLAYER;
									}
								}
							}
						});				
		
		
		stateMachine.addState(new GameState(GameStateKey.WAIT_NEXT_PLAYER_PLAY))
						.addAction(broadcastNextPlayerNotification)
						.addAction(setWaitClaimTimer)
						.addTransition(GameCommandType.LOCAL_BET, GameStateKey.PLAYER_BET) // 跟注， 加注， 跟到底 
						.addEmptyTransition(GameCommandType.LOCAL_CHECK_CARD) // 看牌 
						.addTransition(GameCommandType.LOCAL_FOLD_CARD, GameStateKey.PLAYER_FOLD_CARD) // 弃牌
						.addEmptyTransition(GameCommandType.LOCAL_SHOW_CARD) // 亮牌
						.addTransition(GameCommandType.LOCAL_COMPARE_CARD,GameStateKey.PLAYER_COMPARE_CARD) // 比牌
						.addTransition(GameCommandType.LOCAL_PLAY_USER_QUIT,GameStateKey.PLAY_USER_QUIT)
						.addTransition(GameCommandType.LOCAL_ALL_OTHER_USER_QUIT, GameStateKey.COMPLETE_GAME)
						.addTransition(GameCommandType.LOCAL_OTHER_USER_QUIT,GameStateKey.CHECK_USER_COUNT)
						.addTransition(GameCommandType.LOCAL_TIME_OUT, GameStateKey.TIMEOUT_FOLD_CARD) // 超时没作出选择，视为弃牌
						.addTransition(GameCommandType.NOT_CURRENT_TURN_LOCAL_FOLD_CARD,GameStateKey.COMPLETE_GAME) // 非当前轮玩家弃牌导致游戏可结束
						.addEmptyTransition(GameCommandType.LOCAL_NEW_USER_JOIN) 
						.addAction(clearTimer);	
		
		
		stateMachine.addState(new GameState(GameStateKey.PLAYER_BET))
						.setDecisionPoint(new DecisionPoint(null){
							@Override
							public Object decideNextState(Object context){
									return GameStateKey.SELECT_NEXT_PLAYER;
							}
						 }); 
		
		stateMachine.addState(new GameState(GameStateKey.TIMEOUT_FOLD_CARD))
						.addAction(autoFoldCard)
						.setDecisionPoint(new DecisionPoint(null){
							@Override
							public Object decideNextState(Object context){
								ZjhGameSession session = (ZjhGameSession)context;
								int alivePlayerCount = session.getAlivePlayerCount(); // 此时获得的aliveCount不算上弃牌的用户
								if ( alivePlayerCount < 2 ) 
									return GameStateKey.COMPLETE_GAME;
								else 
									return GameStateKey.SELECT_NEXT_PLAYER;
							}
						});;

		stateMachine.addState(new GameState(GameStateKey.PLAYER_FOLD_CARD))
						.addAction(clearTimer) // 清掉该用户的计时器
						.setDecisionPoint(new DecisionPoint(null){
							@Override
							public Object decideNextState(Object context){
								ZjhGameSession session = (ZjhGameSession)context;
								int alivePlayerCount = session.getAlivePlayerCount(); // 此时获得的aliveCount不算上弃牌的用户
								if ( alivePlayerCount < 2 ) 
									return GameStateKey.COMPLETE_GAME;
								else 
									return GameStateKey.SELECT_NEXT_PLAYER;
							}
						});
		

		stateMachine.addState(new GameState(GameStateKey.PLAYER_COMPARE_CARD))
						.addAction(clearTimer) // 清掉该用户的计时器
						.setDecisionPoint(new DecisionPoint(null){
							@Override
							public Object decideNextState(Object context){
								ZjhGameSession session = (ZjhGameSession)context;
								int alivePlayerCount = session.getAlivePlayerCount();
								if ( alivePlayerCount <= 2 )
									return GameStateKey.COMPLETE_GAME;
								else 
									return GameStateKey.SELECT_NEXT_PLAYER;
							}
						});
		
		
		stateMachine.addState(new GameState(GameStateKey.PLAY_USER_QUIT))
						.addAction(updateQuitPlayerInfo) // 必须在kickPlayUser前执行，因为要用到userId
						.addAction(kickPlayUser)
						.setDecisionPoint(new DecisionPoint(null){
							@Override
							public Object decideNextState(Object context){					
								ZjhGameSession session = (ZjhGameSession)context;
								int alivePlayerCount = session.getAlivePlayerCount();
								if ( alivePlayerCount <= 1 )
									return GameStateKey.COMPLETE_GAME;	
								else
									return GameStateKey.SELECT_NEXT_PLAYER;
							}
						});
					
		
		
		stateMachine.addState(new GameState(GameStateKey.COMPLETE_GAME))
						.addAction(completeGame)
						.setDecisionPoint(new DecisionPoint(null){
							@Override
							public Object decideNextState(Object context){					
								return GameStateKey.SHOW_RESULT;	
							}
						});
	
		
		stateMachine.addState(new GameState(GameStateKey.SHOW_RESULT))
						.addAction(setShowResultTimer)
						.addAction(finishPlayGame)
						.addEmptyTransition(GameCommandType.LOCAL_PLAY_USER_QUIT)
						.addEmptyTransition(GameCommandType.LOCAL_ALL_OTHER_USER_QUIT)
						.addEmptyTransition(GameCommandType.LOCAL_OTHER_USER_QUIT)
						.addEmptyTransition(GameCommandType.LOCAL_NEW_USER_JOIN)			
						.addTransition(GameCommandType.LOCAL_TIME_OUT, GameStateKey.CHECK_USER_COUNT)
						.addAction(clearPlayingStatus)
						.addAction(clearTimer)
						.addAction(restartGame);
	
		stateMachine.printStateMachine();		
		
		return stateMachine;
	} 
    	
}