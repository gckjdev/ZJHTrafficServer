package com.orange.game.zjh.statemachine.action;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.cassandra.cli.CliParser.incrementValue_return;
import org.apache.log4j.Logger;


import com.orange.common.statemachine.Action;
import com.orange.game.traffic.model.dao.GameSession;
import com.orange.game.traffic.robot.client.RobotService;
import com.orange.game.traffic.server.GameEventExecutor;
import com.orange.game.traffic.server.NotificationUtils;
import com.orange.game.traffic.service.GameDBService;
import com.orange.game.traffic.service.SessionUserService;
import com.orange.game.zjh.model.ZjhGameSession;
import com.orange.network.game.protocol.constants.GameConstantsProtos.GameCommandType;
import com.orange.network.game.protocol.message.GameMessageProtos.GameMessage;
import com.orange.network.game.protocol.message.GameMessageProtos.GameStartNotificationRequest;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHGameState;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHUserInfo;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHUserPoker;


public class ZjhGameAction{

//	private static final Logger logger = Logger.getLogger(ZjhGameAction.class.getName()); 
	
	public enum  ZjhTimerType {
		START_GAME, DEAL_AND_WAIT,
		;
	}
	
	public static class NotifyGameStartAndDeal implements Action {
		
		private GameMessage makeGameStartNotification(int totalBet, int singleBet,
				List<PBZJHUserInfo> userPokersInfo) {
			
			PBZJHGameState state = PBZJHGameState.newBuilder()
											.setTotalBet(totalBet)
											.setSingleBet(singleBet)
											.addAllUsersInfo(userPokersInfo)
											.build();
			
			GameStartNotificationRequest request = GameStartNotificationRequest.newBuilder()
											.setZjhGameState(state)
											.build();
			
			GameMessage message = GameMessage.newBuilder()
									.setCommand(GameCommandType.GAME_START_NOTIFICATION_REQUEST)
									.setMessageId(GameEventExecutor.getInstance().generateMessageId())
									.setGameStartNotificationRequest(request)
									.build();
														
			return message;
		}
		
		@Override
		public void execute(Object context) {
			// Needn't check the nullness of the context, it has been checked by the upper-layer method. 
			ZjhGameSession session = (ZjhGameSession)context;
			
			int totalBet = session.getTotalBet();
			int singleBet = session.getSingleBet();
			List<PBZJHUserInfo> userPokersInfo = session.deal();

			GameMessage message = makeGameStartNotification(totalBet, singleBet, userPokersInfo);
			
			NotificationUtils.broadcastNotification(session, message);
		}

	}
	

	public static class ClearAllPlayerData implements Action {

		@Override
		public void execute(Object context) {
			// TODO Auto-generated method stub

		}

	}

	public static class RestartGame implements Action {

		@Override
		public void execute(Object context) {
			// TODO Auto-generated method stub

		}

	}
	
	public static class SetShowResultTimer implements Action {

		@Override
		public void execute(Object context) {
			// TODO Auto-generated method stub

		}

	}

	public static class CompleteGame implements Action {
		
		@Override
		public void execute(Object context) {
			// TODO Auto-generated method stub

		}

	}


	public static class PlayerCompareCard implements Action {

		@Override
		public void execute(Object context) {
			// TODO Auto-generated method stub

		}

	}


	/**
	 * @author larmbr
	 *
	 */
	public static class PlayerShowCard implements Action {

		/* (non-Javadoc)
		 * @see com.orange.common.statemachine.Action#execute(java.lang.Object)
		 */
		@Override
		public void execute(Object context) {
			// TODO Auto-generated method stub

		}

	}


	/**
	 * @author larmbr
	 *
	 */
	public static class PlayerFoldCard implements Action {

		/* (non-Javadoc)
		 * @see com.orange.common.statemachine.Action#execute(java.lang.Object)
		 */
		@Override
		public void execute(Object context) {
			// TODO Auto-generated method stub

		}

	}


	/**
	 * @author larmbr
	 *
	 */
	public static class PlayerCheckCard implements Action {

		/* (non-Javadoc)
		 * @see com.orange.common.statemachine.Action#execute(java.lang.Object)
		 */
		@Override
		public void execute(Object context) {
			// TODO Auto-generated method stub

		}

	}


	/**
	 * @author larmbr
	 *
	 */
	public static class PlayerBet implements Action {

		/* (non-Javadoc)
		 * @see com.orange.common.statemachine.Action#execute(java.lang.Object)
		 */
		@Override
		public void execute(Object context) {
			// TODO Auto-generated method stub

		}

	}


	/**
	 * @author larmbr
	 *
	 */
	public static class SetWaitClaimTimer implements Action {

		/* (non-Javadoc)
		 * @see com.orange.common.statemachine.Action#execute(java.lang.Object)
		 */
		@Override
		public void execute(Object context) {
			// TODO Auto-generated method stub

		}

	}





	public static class KickWaitTimeOutUsers implements Action {

		@Override
		public void execute(Object context) {
			ZjhGameSession session = (ZjhGameSession)context;
			
//			List<String> timeOutUsersList = session.getWaitTimeOutUsers();
//			ServerLog.info(session.getSessionId(), "<KickWaitTimeOutUsers> users="+timeOutUsersList.toString());
//			for (String userId : timeOutUsersList){
//				SessionUserService.getInstance().removeUser(session, userId);
//				session.clearWaitClaimTimeOutTimes(userId);
//			}
//		}

	}
	
	public static class SetWaitClaimTimer implements Action {

		int interval;
		final Object timerType;
		final int normalInterval;
		
		public SetWaitClaimTimer(int interval, Object timerType){
			this.interval = interval;
			this.timerType = timerType;
			this.normalInterval = interval;
		}
		
		private void  getNewInterval(GameSession session) {
			int newInterval = 0;
			if ((newInterval = session.getNewInterval()) != 0) {
				interval = newInterval; 
			}
			interval = normalInterval;
			
		}
		
		private void clearNewInterval(GameSession session) {
			session.setNewInternal(0);
		}
		
		@Override
		public void execute(Object context) {
//			ZjhGameSession session = (ZjhGameSession)context;
//			// check to see if the interval is changed(by last user using decTime item)
//			getNewInterval(session);
//			GameEventExecutor.getInstance().startTimer(session, interval, timerType);
//			// clear it, so the intervel won't influence next user.
//			clearNewInterval(session);
//			// correctly set the  decreaseTimeForNextPlayUser 
//			if ( session.getDecreaseTimeForNextPlayUser() == true ) {
//				session.setDecreaseTimeForNextPlayUser(false);
//			}
		}
	}

	
	public static class ClearWaitClaimTimeOutTimes implements Action {

		@Override
		public void execute(Object context) {
			ZjhGameSession session = (ZjhGameSession)context;
//			session.clearWaitClaimTimeOutTimes(session.getCurrentPlayUserId());
		}

	}
	
	
	public static class SetShowResultTimer implements Action {

//		private static final int SHOW_RESULT_SECONDS_PER_USER = 4;
//		private static final int SHOW_COINS_SECONDS = 3;
//		private static final int EXTRA_SECONDS = 0;

		@Override
		public void execute(Object context) {
//			// TODO Auto-generated method stub
//			ZjhGameSession session = (ZjhGameSession)context;
//			int timeOut = session.getPlayUserCount()*SHOW_RESULT_SECONDS_PER_USER + SHOW_COINS_SECONDS + EXTRA_SECONDS;
//			GameEventExecutor.getInstance().startTimer(session, 
//					timeOut, DiceTimerType.SHOW_RESULT);
//
		}

	}
	
	public static class ClearAllUserPlaying implements Action {

		@Override
		public void execute(Object context) {
			// make all user not playing
			ZjhGameSession session = (ZjhGameSession)context;
			session.getUserList().clearAllUserPlaying();
		}

	}
	public static class KickTakenOverUser implements Action {

		@Override
		public void execute(Object context) {
			// kick all user which are taken over
			ZjhGameSession session = (ZjhGameSession)context;
			SessionUserService.getInstance().kickTakenOverUser(session);
		}

	}
	public static class SelectLoserAsCurrentPlayerUser implements Action {

		@Override
		public void execute(Object context) {
//			ZjhGameSession session = (ZjhGameSession)context;
//			String loserUserId = session.getLoserUserId();
//			if (loserUserId == null){
//				session.selectPlayerUser();
//			}
//			else{
//				ServerLog.info(session.getSessionId(), "try to set loser "+loserUserId+" as current play user");
//				int loserUserIndex = session.getUserIndex(loserUserId);
//				if (loserUserIndex == -1){
//					// loser user doesn't exist
//					session.selectPlayerUser();
//				}
//				else{
//					session.setCurrentPlayUser(loserUserIndex);
//				}
//			}
			
		}

	}
	public static class ClearRobotTimer implements Action {

		@Override
		public void execute(Object context) {
//			GameSession session = (GameSession)context;
//			session.clearRobotTimer();
		}

	}
	public static class PrepareRobot implements Action {

		@Override
		public void execute(Object context) {
			GameSession session = (GameSession)context;
			GameEventExecutor.getInstance().prepareRobotTimer(session, RobotService.getInstance());
		}

	}
	
	
	
	public static class BroadcastNextPlayerNotification implements Action {

		@Override
		public void execute(Object context) {
			ZjhGameSession session = (ZjhGameSession)context;
			NotificationUtils.broadcastNotification(session, null, GameCommandType.NEXT_PLAYER_START_NOTIFICATION_REQUEST);			
		}

	}

	public static class CompleteGame implements Action {

		@Override
		public void execute(Object context) {
//			ZjhGameSession session = (ZjhGameSession)context;
//			
//			int ruleType = session.getRuleType();
//			
//			// all users' dices settlement
//			List<PBDiceFinalCount> diceFinalCountList = session.diceCountSettlement(ruleType);
//			
//			// calculate how many coins that users gain
//			if ( ! diceFinalCountList.equals(Collections.emptyList()) && diceFinalCountList.size() >= 2 ) { // only meaningful for at least 2 users
//				int allFinalCount = 0 ; // all user total final count
//				for ( PBDiceFinalCount finalCount: diceFinalCountList ) {
//					allFinalCount += finalCount.getFinalDiceCount();
//				}
//				session.calculateCoins(allFinalCount,ruleType );
//			}
//			
//			// save result into db
//			saveUserResultIntoDB(session);
//			
//			// charge/deduct coins
//			writeUserCoinsIntoDB(session);
//			
//			// broadcast complete complete with result
//			PBDiceGameResult result = PBDiceGameResult.newBuilder()
//				.addAllUserResult(session.getUserResults())
//				.addAllFinalCount(diceFinalCountList)
//				.build();
//				
//			GameOverNotificationRequest notification = GameOverNotificationRequest.newBuilder()
//				.setGameResult(result)
//				.build();
//			
//			GameMessageProtos.GameMessage.Builder builder = GameMessageProtos.GameMessage.newBuilder()
//				.setCommand(GameCommandType.GAME_OVER_NOTIFICATION_REQUEST)
//				.setMessageId(GameEventExecutor.getInstance().generateMessageId())
//				.setSessionId(session.getSessionId())
//				.setGameOverNotificationRequest(notification);				
//			
//			if (session.getCurrentPlayUserId() != null){
//				builder.setCurrentPlayUserId(session.getCurrentPlayUserId());
//			}
//		
//			GameMessage message = builder.build();
//			ServerLog.info(session.getSessionId(), "send game over="+message.toString());
//			NotificationUtils.broadcastNotification(session, null, message);
//				
		}

		private void writeUserCoinsIntoDB(final ZjhGameSession session) {
			final GameDBService dbService = GameDBService.getInstance();
			dbService.executeDBRequest(session.getSessionId()	, new Runnable() {
				
				@Override
				public void run() {
//					
//					MongoDBClient dbClient = dbService.getMongoDBClient(session.getSessionId());
//					
//					Collection<PBUserResult> resultList = session.getUserResults();
//					for (PBUserResult result : resultList){
//						boolean win = result.getWin();
//						String userId = result.getUserId();
//						int amount = result.getGainCoins();
//						
//						if (win){
//							UserManager.chargeAccount(dbClient, userId, amount, DBConstants.C_CHARGE_SOURCE_DICE_WIN, null, null);
//						}
//						else{
//							UserManager.deductAccount(dbClient, userId, -amount, DBConstants.C_CHARGE_SOURCE_DICE_WIN);
//						}
//					}
				}
			});
		}

		private void saveUserResultIntoDB(final ZjhGameSession session) {
			final GameDBService dbService = GameDBService.getInstance();
			dbService.executeDBRequest(session.getSessionId()	, new Runnable() {
				
				@Override
				public void run() {
//
//					int sessionId = session.getSessionId();
//					MongoDBClient dbClient = dbService.getMongoDBClient(sessionId);
//					
//					Collection<PBUserResult> resultList = session.getUserResults();
//					List<GameUser> gameUserList = session.getUserList().getUserList();
//					
//					Set<String> gameResultUserIdSet = new HashSet<String>();
//					
//					// update the winner and loser
//					for ( PBUserResult result : resultList ) {
//						String userId = result.getUserId();
//						// record which two users get userResults
//						gameResultUserIdSet.add(userId);
//						
// 						queryAndUpdate(sessionId, dbClient, userId, result);
//					}
//					
//					// update other players
//					for(GameUser gameUser : gameUserList) {
//						String userId = gameUser.getUserId();
//						if ( gameUser.isPlaying() == true  && !gameResultUserIdSet.contains(userId)) {
//							queryAndUpdate(sessionId, dbClient, userId, null);
//						}
//					}
//					
				}

//				private void queryAndUpdate(int sessionId, MongoDBClient dbClient, String userIdString, PBUserResult result) {
//					
//					// query by user_id and game_id
//					DBObject query = new BasicDBObject();
//					query.put(DBConstants.F_USERID, userIdString);
//					query.put(DBConstants.F_GAMEID, DBConstants.GAME_ID_DICE);
//
//					// update
//					DBObject update = new BasicDBObject();
//					DBObject incUpdate = new BasicDBObject();
//					DBObject dateUpdate = new BasicDBObject();
//					
//					incUpdate.put(DBConstants.F_PLAY_TIMES, 1);
//					if ( result != null ) {
//						if (result.getWin() == true) {
//							incUpdate.put(DBConstants.F_WIN_TIMES, 1);
//						} else {
//							incUpdate.put(DBConstants.F_LOSE_TIMES, 1);
//						}
//					}
//					dateUpdate.put(DBConstants.F_MODIFY_DATE, new Date());
//					
//					update.put("$inc", incUpdate);
//					update.put("$set", dateUpdate);
//
//					ServerLog.info(sessionId, "<updateUserResult> query="+query.toString()+", update="+update.toString());
//					dbClient.upsertAll(DBConstants.T_USER_GAME_RESULT, query, update);
//				}
			});
		}
//
	}

	public static class RestartGame implements Action {

		@Override
		public void execute(Object context) {
//			ZjhGameSession session = (ZjhGameSession)context;
//			session.restartGame();
//			SessionUserService.getInstance().kickTakenOverUser(session);			
//			return;
		}
    };
}
}
