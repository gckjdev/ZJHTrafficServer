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

    
    private static final int START_GAME_TIMEOUT = 3;
    private static final int DEAL_AND_WAIT_TIMEOUT = 0; // usercout * perUserTime


    
	@Override
	public StateMachine buildStateMachine() {
		
		StateMachine stateMachine = new StateMachine();
		
		
		Action initGame        		    = new CommonGameAction.InitGame();
		Action clearTimer		 			 = new CommonGameAction.ClearTimer();
		Action selectPlayer 			 	 = new CommonGameAction.SelectPlayUser();
		Action broadcastNextPlayerNotification
												 = new CommonGameAction.BroadcastPlayUserChange();
		Action setStartGameTimer 		 = new CommonGameAction.CommonTimer(START_GAME_TIMEOUT, ZjhGameAction.ZjhTimerType.START_GAME);
		Action notifyGameStartAndDealTimer 
												 = new CommonGameAction.CommonTimer(DEAL_AND_WAIT_TIMEOUT, ZjhGameAction.ZjhTimerType.DEAL_AND_WAIT);
		Action kickPlayUser     		 = new CommonGameAction.KickPlayUser(); 
		Action notifyGameStartAndDeal  = new ZjhGameAction.NotifyGameStartAndDeal(); 
		Action setWaitClaimTimer		 = new ZjhGameAction.SetWaitClaimTimer();
		Action completeGame				 = new ZjhGameAction.CompleteGame();
		Action setShowResultTimer		 = new ZjhGameAction.SetShowResultTimer();
		Action clearAllPlayerData		 = new ZjhGameAction.ClearAllPlayerData();
		Action restartGame				 = new ZjhGameAction.RestartGame();	
		
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
						.addTransition(GameCommandType.LOCAL_NEW_USER_JOIN, GameStateKey.CHECK_USER_COUNT)
						.addTransition(GameCommandType.LOCAL_USER_QUIT, GameStateKey.CHECK_USER_COUNT);
		
		stateMachine.addState(new GameState(GameStateKey.WAIT_FOR_START_GAME))
						.addAction(setStartGameTimer)
						.addTransition(GameCommandType.LOCAL_USER_QUIT, GameStateKey.PLAY_USER_QUIT)
						.addTransition(GameCommandType.LOCAL_TIME_OUT, GameStateKey.DEAL)				
						.addAction(clearTimer);
		
		
		stateMachine.addState(new GameState(GameStateKey.DEAL))
						.addAction(notifyGameStartAndDealTimer)
						.addAction(notifyGameStartAndDeal)
						.addTransition(GameCommandType.LOCAL_PLAY_USER_QUIT, GameStateKey.PLAY_USER_QUIT)  // current playing user quit
						.addTransition(GameCommandType.LOCAL_ALL_OTHER_USER_QUIT,GameStateKey.CREATE)
						.addTransition(GameCommandType.LOCAL_OTHER_USER_QUIT, GameStateKey.CHECK_USER_COUNT)
						.addTransition(GameCommandType.LOCAL_TIME_OUT, GameStateKey.SELECT_NEXT_PLAYER)
						.addEmptyTransition(GameCommandType.LOCAL_NEW_USER_JOIN)  // Looker-on join
						.addAction(clearTimer);
						
		
		
		stateMachine.addState(new GameState(GameStateKey.SELECT_NEXT_PLAYER))
						.addAction(clearTimer) // to clear last player's timer
						.addAction(selectPlayer)
						.setDecisionPoint(new DecisionPoint(null) {
							@Override
							public Object decideNextState(Object context){
								ZjhGameSession session = (ZjhGameSession)context;
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
						});				
		
		
		stateMachine.addState(new GameState(GameStateKey.WAIT_NEXT_PLAYER_PLAY))
						.addAction(broadcastNextPlayerNotification)
						.addAction(setWaitClaimTimer)
						.addTransition(GameCommandType.LOCAL_BET, GameStateKey.PLAYER_BET) // 跟注， 加注， 跟到底 
						.addTransition(GameCommandType.LOCAL_CHECK_CARD,GameStateKey.PLAYER_CHECK_CARD) // 看牌 
						.addTransition(GameCommandType.LOCAL_FOLD_CARD, GameStateKey.PLAYER_FOLD_CARD) // 弃牌
						.addTransition(GameCommandType.LOCAL_SHOW_CARD,GameStateKey.PLAYER_SHOW_CARD) // 亮牌
						.addTransition(GameCommandType.LOCAL_COMPARE_CARD,GameStateKey.PLAYER_COMPARE_CARD) // 比牌
						.addTransition(GameCommandType.LOCAL_PLAY_USER_QUIT,GameStateKey.PLAY_USER_QUIT)
						.addTransition(GameCommandType.LOCAL_ALL_OTHER_USER_QUIT, GameStateKey.CREATE)
						.addTransition(GameCommandType.LOCAL_OTHER_USER_QUIT,GameStateKey.CHECK_USER_COUNT)
						.addEmptyTransition(GameCommandType.LOCAL_NEW_USER_JOIN) 
						.addAction(clearTimer);	
		
		
		stateMachine.addState(new GameState(GameStateKey.PLAYER_BET))
						.setDecisionPoint(new DecisionPoint(null){
							@Override
							public Object decideNextState(Object context){
									return GameStateKey.SELECT_NEXT_PLAYER;
							}
						 }); 
		
		
		stateMachine.addState(new GameState(GameStateKey.PLAYER_CHECK_CARD))
						.addTransition(GameCommandType.LOCAL_BET,GameStateKey.PLAYER_BET)  
						.addTransition(GameCommandType.LOCAL_FOLD_CARD, GameStateKey.PLAYER_FOLD_CARD) // 弃牌
						.addTransition(GameCommandType.LOCAL_SHOW_CARD,GameStateKey.PLAYER_SHOW_CARD) // 亮牌
						.addTransition(GameCommandType.LOCAL_COMPARE_CARD,GameStateKey.PLAYER_COMPARE_CARD) // 比牌
						.addTransition(GameCommandType.LOCAL_TIME_OUT, GameStateKey.PLAYER_FOLD_CARD) // 超时没作出选择，视为弃牌
						.addTransition(GameCommandType.LOCAL_PLAY_USER_QUIT,GameStateKey.PLAY_USER_QUIT)
						.addTransition(GameCommandType.LOCAL_ALL_OTHER_USER_QUIT, GameStateKey.CREATE)
						.addTransition(GameCommandType.LOCAL_OTHER_USER_QUIT,GameStateKey.CHECK_USER_COUNT)
						.addEmptyTransition(GameCommandType.LOCAL_NEW_USER_JOIN); 
		
		stateMachine.addState(new GameState(GameStateKey.PLAYER_FOLD_CARD))
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
		
		stateMachine.addState(new GameState(GameStateKey.PLAYER_SHOW_CARD))
						.addTransition(GameCommandType.LOCAL_BET,GameStateKey.PLAYER_BET) // 看牌 
						.addTransition(GameCommandType.LOCAL_FOLD_CARD, GameStateKey.PLAYER_FOLD_CARD) // 弃牌
						.addTransition(GameCommandType.LOCAL_COMPARE_CARD,GameStateKey.PLAYER_COMPARE_CARD) // 比牌
						.addTransition(GameCommandType.LOCAL_TIME_OUT, GameStateKey.PLAYER_FOLD_CARD) // 超时没作出选择，视为弃牌
						.addTransition(GameCommandType.LOCAL_PLAY_USER_QUIT,GameStateKey.PLAY_USER_QUIT)
						.addTransition(GameCommandType.LOCAL_ALL_OTHER_USER_QUIT, GameStateKey.CREATE)
						.addTransition(GameCommandType.LOCAL_OTHER_USER_QUIT,GameStateKey.CHECK_USER_COUNT)
						.addEmptyTransition(GameCommandType.LOCAL_NEW_USER_JOIN) 
						.addAction(clearTimer);	
		
		
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
								return GameStateKey.SHOW_RESULT;	// goto check user count state directly
							}
						});
	
		
		stateMachine.addState(new GameState(GameStateKey.SHOW_RESULT))
						.addAction(setShowResultTimer)
						.addEmptyTransition(GameCommandType.LOCAL_PLAY_USER_QUIT)
						.addEmptyTransition(GameCommandType.LOCAL_ALL_OTHER_USER_QUIT)
						.addEmptyTransition(GameCommandType.LOCAL_OTHER_USER_QUIT)
						.addEmptyTransition(GameCommandType.LOCAL_NEW_USER_JOIN)			
						.addTransition(GameCommandType.LOCAL_TIME_OUT, GameStateKey.CHECK_USER_COUNT)
						.addAction(clearAllPlayerData)
						.addAction(restartGame);
	
		stateMachine.printStateMachine();		
		
		return stateMachine;
	} 
    

	
}

