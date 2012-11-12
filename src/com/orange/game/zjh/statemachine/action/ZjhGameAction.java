package com.orange.game.zjh.statemachine.action;

import java.util.Collection;
import java.util.List;

import com.orange.common.log.ServerLog;
import com.orange.common.statemachine.Action;
import com.orange.game.constants.DBConstants;
import com.orange.game.traffic.model.dao.GameSession;
import com.orange.game.traffic.server.GameEventExecutor;
import com.orange.game.traffic.server.NotificationUtils;
import com.orange.game.traffic.service.UserGameResultService;
import com.orange.game.zjh.model.ZjhGameSession;
import com.orange.network.game.protocol.constants.GameConstantsProtos.GameCommandType;
import com.orange.network.game.protocol.message.GameMessageProtos;
import com.orange.network.game.protocol.message.GameMessageProtos.GameMessage;
import com.orange.network.game.protocol.message.GameMessageProtos.GameOverNotificationRequest;
import com.orange.network.game.protocol.message.GameMessageProtos.GameStartNotificationRequest;
import com.orange.network.game.protocol.model.GameBasicProtos.PBUserResult;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHGameResult;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHGameState;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHUserPlayInfo;


public class ZjhGameAction{

	public enum  ZjhTimerType {
		START_GAME, DEAL_AND_WAIT, WAIT_CLAIM, SHOW_RESULT, 
		NOTIFY_GAME_START_AND_DEAL, SELECT_PLAYER_WAIT, COMPLTE_WAIT,
		;
	}
	
	public static class setCompleteGameTimer implements Action {
		
		private static final int COMPLETE_WAIT_TIMEOUT = 3;

		@Override
		public void execute(Object context) {
			ZjhGameSession session = (ZjhGameSession)context;
			int timeOut = COMPLETE_WAIT_TIMEOUT;
			GameEventExecutor.getInstance().startTimer(session, 
					timeOut, ZjhTimerType.COMPLTE_WAIT);
		}
	}


	
	public static class SetSelectPlayerWaitTimer implements Action {

		private static final int SELECT_PLAYER_WAIT_TIMEOUT = 3;

		@Override
		public void execute(Object context) {
			ZjhGameSession session = (ZjhGameSession)context;
			int timeOut = SELECT_PLAYER_WAIT_TIMEOUT;
			GameEventExecutor.getInstance().startTimer(session, 
					timeOut, ZjhTimerType.SELECT_PLAYER_WAIT);
		}
	}


	public static class NotifyGameStartAndDealTimer implements Action {

		private final static double PER_USER_TIME_SHARE = 0.33;
		private final static int EXTRA_TIME = 2;
		
		private long calculateTimeout(int playerCount) {
			
			double tmp = playerCount * PER_USER_TIME_SHARE;
			return Math.round(tmp) + EXTRA_TIME ;
		}
		@Override
		public void execute(Object context) {
			ZjhGameSession session = (ZjhGameSession)context;
			long timeOut = calculateTimeout(session.getPlayUserCount());
					GameEventExecutor.getInstance().startTimer(session, 
					timeOut, ZjhTimerType.NOTIFY_GAME_START_AND_DEAL);
		}

	}

	public static class AutoFoldCard implements Action {

		@Override
		public void execute(Object context) {
			ZjhGameSession session = (ZjhGameSession)context;
			String userId = session.getCurrentPlayUserId();
			ServerLog.info(session.getSessionId(), "auto fold card user is " + userId);
			session.foldCard(userId);
		}

	}

	public static class SetAlivePlayerCout implements Action {

		@Override
		public void execute(Object context) {
			ZjhGameSession session = (ZjhGameSession)context;
			int playUserCount = session.getPlayUserCount();
			session.setAlivePlayerCount(playUserCount);
		}

	}


	public static class SetTotalBet implements Action {

		@Override
		public void execute(Object context) {
			ZjhGameSession session = (ZjhGameSession)context;
			session.setTotalBet(session.getPlayUserCount());
		}

	}
	
	
		public static class NotifyGameStartAndDeal implements Action {
		
			private GameMessage makeGameStartNotification(int totalBet, int singleBet,
					List<PBZJHUserPlayInfo> userPokersInfo) {
			
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
					.setGameStartNotificationRequest(request).build();

				return message;
			}
			
		
		@Override
		public void execute(Object context) {
			ZjhGameSession session = (ZjhGameSession)context;
			
			int totalBet = session.getTotalBet();
			int singleBet = session.getSingleBet();
			ServerLog.info(session.getSessionId(), "<NotifyGameStartAndDeal>" +
					"totalBet="+totalBet+", singleBet="+singleBet);
			List<PBZJHUserPlayInfo> userPlayInfo = session.deal();

			GameMessage startMessage = makeGameStartNotification(totalBet, singleBet, userPlayInfo);
			NotificationUtils.broadcastNotification(session, startMessage);
			
		}

	}
	
	public static class BroadcastNextPlayerNotification implements Action {

			@Override
			public void execute(Object context) {
				ZjhGameSession session = (ZjhGameSession)context;
				NotificationUtils.broadcastNotification(session, null, GameCommandType.NEXT_PLAYER_START_NOTIFICATION_REQUEST);			
			}

	}
	
	public static class SetShowResultTimer implements Action {

		private static final int SHOW_RESULT_TIMEOUT = 7;

		@Override
		public void execute(Object context) {
			ZjhGameSession session = (ZjhGameSession)context;
			int timeOut = SHOW_RESULT_TIMEOUT;
			GameEventExecutor.getInstance().startTimer(session, 
					timeOut, ZjhTimerType.SHOW_RESULT);

		}

	}

	public static class CompleteGame implements Action {
		
		private UserGameResultService service = UserGameResultService.getInstance();
		
		@Override
		public void execute(Object context) {
			
			ZjhGameSession session = (ZjhGameSession)context;
			
			Collection<PBUserResult> userResult = session.judgeWhoWins() ;
					
			// If userResult's size is not 1, which means it only contains the winner's result.
			// Since all other user has been sync to db before.
			if ( userResult.size() != 1 ) {
				String exceptionString = "<CompleteGame> PBUserResult contains not only one element: "+userResult.toString();
				ServerLog.error(session.getSessionId(), new Exception(exceptionString));
			}
			ServerLog.info(session.getSessionId(), "<completeGame> userResult is " +userResult.toString());
			
			// write game result(playtimes, wintime, losetimes, etc) into db
			for ( PBUserResult result : userResult ) {
				service.writeUserGameResultIndoDb(session.getSessionId(), result, DBConstants.GAME_ID_ZJH);
			}
			
			// charge/deduct coins
			service.writeAllUserCoinsIntoDB(session,DBConstants.C_CHARGE_SOURCE_ZJH_WIN);
			
			
			// broadcast complete complete with result
			PBZJHGameResult result = PBZJHGameResult.newBuilder()
			    	.addAllUserResult(userResult)
					.build();
				
			GameOverNotificationRequest notification = GameOverNotificationRequest.newBuilder()
				.setZJHGameResult(result)
				.build();
			
			GameMessageProtos.GameMessage.Builder builder = GameMessageProtos.GameMessage.newBuilder()
				.setCommand(GameCommandType.GAME_OVER_NOTIFICATION_REQUEST)
				.setMessageId(GameEventExecutor.getInstance().generateMessageId())
				.setSessionId(session.getSessionId())
				.setGameOverNotificationRequest(notification);				
			
			if (session.getCurrentPlayUserId() != null){
				builder.setCurrentPlayUserId(session.getCurrentPlayUserId());
			}
		
			GameMessage message = builder.build();
			ServerLog.info(session.getSessionId(), "send game over="+message.toString());
			NotificationUtils.broadcastNotification(session, null, message);
		}

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
			ZjhGameSession session = (ZjhGameSession)context;
			// check to see if the interval is changed(by last user using decTime item)
			getNewInterval(session);
			GameEventExecutor.getInstance().startTimer(session, interval, timerType);
			// clear it, so the intervel won't influence next user.
			clearNewInterval(session);
			// correctly set the  decreaseTimeForNextPlayUser 
//			if ( session.getDecreaseTimeForNextPlayUser() == true ) {
//				session.setDecreaseTimeForNextPlayUser(false);
//			}
		}
	}
	
	public static class ClearAllUserPlaying implements Action {

		@Override
		public void execute(Object context) {
			// make all user not playing
			ZjhGameSession session = (ZjhGameSession)context;
			session.getUserList().setAllPlayerLoseGameToFalse();
		}

	}

	
	public static class RestartGame implements Action {

		@Override
		public void execute(Object context) {
			ZjhGameSession session = (ZjhGameSession)context;
			session.restartGame();
		}
	}


	
	public static class UpdateQuitPlayerInfo implements Action {

		@Override
		public void execute(Object context) {
			ZjhGameSession session = (ZjhGameSession)context;
			session.updateQuitPlayerInfo(session.getCurrentPlayUserId());
		}

	}


	public static class SetAllPlayerLoseGameToFalse implements Action {

		@Override
		public void execute(Object context) {
			// make all user not playing
			ZjhGameSession session = (ZjhGameSession)context;
			session.getUserList().setAllPlayerLoseGameToFalse();
		}
	}


	
	public static class ClearAllPlayingStatus implements Action {

		@Override
		public void execute(Object context) {
			ZjhGameSession session = (ZjhGameSession)context;
			session.getUserList().clearAllUserPlaying();
		}
	}
	
}
