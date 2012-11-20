package com.orange.game.zjh.robot.client;


import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.math.RandomUtils;

import com.orange.common.log.ServerLog;
import com.orange.game.constants.DBConstants;
import com.orange.game.model.dao.User;
import com.orange.game.traffic.robot.client.AbstractRobotClient;
import com.orange.game.traffic.server.GameEventExecutor;
import com.orange.game.zjh.model.ZjhGameSession;
import com.orange.network.game.protocol.constants.GameConstantsProtos.GameCommandType;
import com.orange.network.game.protocol.message.GameMessageProtos.BetRequest;
import com.orange.network.game.protocol.message.GameMessageProtos.CheckCardRequest;
import com.orange.network.game.protocol.message.GameMessageProtos.CompareCardRequest;
import com.orange.network.game.protocol.message.GameMessageProtos.FoldCardRequest;
import com.orange.network.game.protocol.message.GameMessageProtos.GameMessage;
import com.orange.network.game.protocol.message.GameMessageProtos.ShowCardRequest;
import com.orange.network.game.protocol.model.GameBasicProtos.PBGameUser;
import com.orange.network.game.protocol.model.GameBasicProtos.PBGameUser.Builder;


public class ZjhRobotClient extends AbstractRobotClient {

	
	private final ScheduledExecutorService scheduleService = Executors.newScheduledThreadPool(5);
	private ScheduledFuture<?> checkCardTimerFuture = null;
	private ScheduledFuture<?> commonTimerFuture = null;

	private static final int IDX_CHECK = 0;
	private static final int IDX_FOLD = 1;
	private static final int IDX_BET = 2;
	private static final int IDX_RAISE_BET = 3;
//	private static final int IDX_AUTO_BET = 4;
	private static final int IDX_COMPARE = 5;
	private static final int IDX_SHOW = 6;
	
	
	private ZjhRobotIntelligence robotIntelligence = new ZjhRobotIntelligence(sessionId, userId, nickName);
	
	public ZjhRobotClient(User user, int sessionId, int index) {
		super(user, sessionId,index);
		oldExp = experience = user.getExpByAppId(DBConstants.APPID_ZHAJINHUA);
		level = user.getLevelByAppId(DBConstants.APPID_ZHAJINHUA); 
		balance = user.getBalance();
	}
	
	@Override
	public void handleMessage(GameMessage message){
		
		switch (message.getCommand()){
		
			case GAME_START_NOTIFICATION_REQUEST:
				
				robotIntelligence.introspectPokers(message.getGameStartNotificationRequest());
				if ( robotIntelligence.canCheckCardNow()) {
					scheduleCheckCard(10 + RandomUtils.nextInt(20));
				}
				break;
			case NEXT_PLAYER_START_NOTIFICATION_REQUEST:
				if (message.getCurrentPlayUserId().equals(userId)){
					if ( robotIntelligence.needToPlay() ) {
						if ( robotIntelligence.decision[IDX_CHECK]) {
							scheduleCheckCard(1);
						}
						if ( robotIntelligence.decision[IDX_SHOW] ) {
							scheduleSendMessage(3, makeShowCardMessage());
						}
						if ( robotIntelligence.decision[IDX_BET] || robotIntelligence.decision[IDX_RAISE_BET]) {
							scheduleSendMessage(4 + RandomUtils.nextInt(5), makeBetMessage());
						} else if ( robotIntelligence.decision[IDX_COMPARE]) {
							scheduleSendMessage(4 + RandomUtils.nextInt(5), makeCompareCardMessage());
						} else if (robotIntelligence.decision[IDX_FOLD]) {
							scheduleSendMessage(4 + RandomUtils.nextInt(5), makeFoldCardMessage());
						} else {
							// 默认跟注
							scheduleSendMessage(2 + RandomUtils.nextInt(5), makeBetMessage());
						}
						robotIntelligence.cleanDecision();
					}
				}
				break;
			case BET_REQUEST:
				robotIntelligence.handleBetRequest(message.getUserId(), message.getBetRequest());
				break;
			case CHECK_CARD_REQUEST:
				break;
			case COMPARE_CARD_REQUEST:
				break;
			case COMPARE_CARD_RESPONSE:
				robotIntelligence.handleCompareResponse(message.getCompareCardResponse());
				break;
			case FOLD_CARD_REQUEST:
				break;
			case SHOW_CARD_REQUEST: 
				break;
			default:
				break;
		}
	}
	
	private void scheduleSendMessage(int delay, Runnable message) {
		
		if ( commonTimerFuture != null ) {
			commonTimerFuture.cancel(false);
		}
		
		commonTimerFuture = scheduleService.schedule(message, delay, TimeUnit.SECONDS);
	}
	
	private void scheduleCheckCard(int delay) {
		
		if ( checkCardTimerFuture != null ) {
			checkCardTimerFuture.cancel(false);
		}
		
		checkCardTimerFuture = scheduleService.schedule(makeCheckCardMessage(), delay, TimeUnit.SECONDS);
	}
	
	private Runnable makeCheckCardMessage() {
		
		CheckCardRequest request = CheckCardRequest.newBuilder()
				.build();
		
		final GameMessage checkCardMessage = GameMessage.newBuilder()
				.setCheckCardRequest(request)
				.setMessageId(getClientIndex())
				.setCommand(GameCommandType.CHECK_CARD_REQUEST)
				.setUserId(userId)
				.setSessionId(sessionId)
				.build();
		
		return new Runnable() {
			@Override
			public void run() {
				send(checkCardMessage);
				ServerLog.info(sessionId, "Robot "+ nickName +" check card Now");
				robotIntelligence.setHasCheckedCard();
			}
		}; 
	}
	
	private Runnable makeBetMessage() {
		
		ServerLog.info(sessionId, "Robot "+nickName+" bets");
		
		int singleBet = robotIntelligence.getSingleBet();
		int count = (robotIntelligence.hasCheckedCard() ? 2 : 1);
		boolean isAutoBet = false;
				
		BetRequest request = BetRequest.newBuilder()
				.setSingleBet(singleBet)
				.setCount(count)
				.setIsAutoBet(isAutoBet)
				.build();
		
		final GameMessage betMessage = GameMessage.newBuilder()
				.setBetRequest(request)
				.setMessageId(getClientIndex())
				.setCommand(GameCommandType.BET_REQUEST)
				.setUserId(userId)
				.setSessionId(sessionId)
				.build();
		
		return new Runnable() {
			@Override
			public void run() {
				send(betMessage);
			}
		};
	}
	
	private Runnable makeCompareCardMessage() {
		
		String toUserId = robotIntelligence.getCompareToUserId();
		
		CompareCardRequest request = CompareCardRequest.newBuilder()
				.setToUserId(toUserId)
				.build();
		
		final GameMessage compareCardMessage = GameMessage.newBuilder()
				.setCompareCardRequest(request)
				.setMessageId(getClientIndex())
				.setCommand(GameCommandType.COMPARE_CARD_REQUEST)
				.setUserId(userId)
				.setSessionId(sessionId)
				.build();
		
		return new Runnable() {
			@Override
			public void run() {
				send(compareCardMessage);
			}
		};
	}
	
	
	private Runnable makeFoldCardMessage() {
		
		FoldCardRequest request = FoldCardRequest.newBuilder()
				.build();
		
		final GameMessage foldCardMessage = GameMessage.newBuilder()
				.setFoldCardRequest(request)
				.setMessageId(getClientIndex())
				.setCommand(GameCommandType.FOLD_CARD_REQUEST)
				.setUserId(userId)
				.setSessionId(sessionId)
				.build();

		return new Runnable() {
			@Override
			public void run() {
				send(foldCardMessage);
				robotIntelligence.setFoldedCard();
			}
		};
	}
	
	
	private Runnable makeShowCardMessage() {
		
		List<Integer> cardIds = robotIntelligence.getShowCardIds();
		
		ShowCardRequest request = ShowCardRequest.newBuilder()
				.addAllCardIds(cardIds)
				.build();
		
		final GameMessage showCardMessage = GameMessage.newBuilder()
				.setShowCardRequest(request)
				.setMessageId(getClientIndex())
				.setCommand(GameCommandType.SHOW_CARD_REQUEST)
				.setUserId(userId)
				.setSessionId(sessionId)
				.build();

		return new Runnable() {
			@Override
			public void run() {
				send(showCardMessage);
			}
		};
	}
	

	@Override
	public void resetPlayData(boolean robotWinThisGame) {
		robotIntelligence.resetPlayData();
	}
	
	@Override
	public String getAppId() {
		// TODO: a fake appId, you'd better not call this method.
		return DBConstants.APPID_ZHAJINHUA;
	}
	
	@Override
	public String getGameId() {
		return DBConstants.GAME_ID_ZJH;
	}		
	
	@Override
	public PBGameUser toPBGameUserSpecificPart(Builder builder) {
		return builder.build();
	}
}
