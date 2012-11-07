package com.orange.game.zjh.statemachine.action;

import java.util.Collection;
import java.util.List;

import com.orange.common.log.ServerLog;
import com.orange.common.statemachine.Action;
import com.orange.game.traffic.model.dao.GameSession;
import com.orange.game.traffic.model.dao.GameUser;
import com.orange.game.traffic.robot.client.RobotService;
import com.orange.game.traffic.server.GameEventExecutor;
import com.orange.game.traffic.server.HandlerUtils;
import com.orange.game.traffic.server.NotificationUtils;
import com.orange.game.zjh.model.ZjhGameSession;
import com.orange.network.game.protocol.constants.GameConstantsProtos.GameCommandType;
import com.orange.network.game.protocol.message.GameMessageProtos;
import com.orange.network.game.protocol.message.GameMessageProtos.BetRequest;
import com.orange.network.game.protocol.message.GameMessageProtos.GameMessage;
import com.orange.network.game.protocol.message.GameMessageProtos.GameOverNotificationRequest;
import com.orange.network.game.protocol.message.GameMessageProtos.GameStartNotificationRequest;
import com.orange.network.game.protocol.model.GameBasicProtos.PBUserResult;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHGameResult;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHGameState;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHUserPlayInfo;


public class ZjhGameAction{

	public static class SetAllPlayerLoseGameToFalse implements Action {

		@Override
		public void execute(Object context) {
			// make all user not playing
			ZjhGameSession session = (ZjhGameSession)context;
			session.getUserList().setAllPlayerLoseGameToFalse();
		}
	}


	public enum  ZjhTimerType {
		START_GAME, DEAL_AND_WAIT, WAIT_CLAIM, SHOW_RESULT, NOTIFY_GAME_START_AND_DEAL,
		;
	}

	
	public static class ClearAllPlayingStatus implements Action {

		@Override
		public void execute(Object context) {
			ZjhGameSession session = (ZjhGameSession)context;
			session.getUserList().setAllPlayerLoseGameToFalse();
		}
	}

	
	public static class NotifyGameStartAndDealTimer implements Action {

		double PER_USER_TIME_SHARE = 0.33;

		private long calculateTimeout(int playerCount) {
			
			double tmp = playerCount * PER_USER_TIME_SHARE;
			return Math.round(tmp) + 1 ;
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
			session.foldCard(session.getCurrentPlayUserId());
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
	
	
		public static class PrepareRobot implements Action {

			@Override
			public void execute(Object context) {
				GameSession session = (GameSession)context;
				GameEventExecutor.getInstance().prepareRobotTimer(session, RobotService.getInstance());
			}

		}
		
		public static class ClearRobotTimer implements Action {

			@Override
			public void execute(Object context) {
				GameSession session = (GameSession)context;
				session.clearRobotTimer();
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
			
			private void broadcastBetNofication(GameSession session,int singleBet, int count, boolean isAutoBet) {

				List<GameUser> list = session.getUserList().getAllUsers();
				for (GameUser user : list){		
					BetRequest request = BetRequest.newBuilder()
							.setSingleBet(singleBet)
							.setCount(count)
							.setIsAutoBet(isAutoBet)
							.build();
				
					GameMessage message = GameMessage.newBuilder()
						.setCommand(GameCommandType.BET_REQUEST)
						.setMessageId(GameEventExecutor.getInstance().generateMessageId())
						.setBetRequest(request)
						.setUserId(user.getUserId())
						.build();

					ServerLog.info(session.getSessionId(), "betNotification for "
							+ user.getUserId() +" is :"+ message.toString());
					// send notification for the user			
					HandlerUtils.sendMessage(message, user.getChannel());
				}	
				
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
			
			broadcastBetNofication(session, singleBet, 1, false);
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
		
		@Override
		public void execute(Object context) {
			
			ZjhGameSession session = (ZjhGameSession)context;
			
			session.judgeWhowins();
			
			Collection<PBUserResult> userResult = session.getUserResults() ;
					
			ServerLog.info(session.getSessionId(), "<completeGame> userResult is " +userResult.toString());
			
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


}
