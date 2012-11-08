package com.orange.game.zjh.robot.client;


import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.math.RandomUtils;

import com.mongodb.DBObject;
import com.orange.common.log.ServerLog;
import com.orange.common.mongodb.MongoDBClient;
import com.orange.game.constants.DBConstants;
import com.orange.game.constants.ServiceConstant;
import com.orange.game.model.dao.User;
import com.orange.game.model.manager.UserManager;
import com.orange.game.traffic.robot.client.AbstractRobotClient;
import com.orange.network.game.protocol.constants.GameConstantsProtos.GameCommandType;
import com.orange.network.game.protocol.message.GameMessageProtos.BetRequest;
import com.orange.network.game.protocol.message.GameMessageProtos.FoldCardRequest;
import com.orange.network.game.protocol.message.GameMessageProtos.GameMessage;
import com.orange.network.game.protocol.model.GameBasicProtos.PBGameUser;
import com.orange.network.game.protocol.model.GameBasicProtos.PBGameUser.Builder;


public class ZjhRobotClient extends AbstractRobotClient {

//	private final static Logger logger = Logger.getLogger(ZjhRobotClient.class.getName());
	
	private final ScheduledExecutorService scheduleService = Executors.newScheduledThreadPool(5);
	private ScheduledFuture<?> FoldCardTimerFuture = null;
	private Object shareTimerType = null;
	private Object foldCardTimerFuture = null;
	
	private int betTimes = 0;
	private int singleBet = 5;
	private int count = 1;
	
	public ZjhRobotClient(User user, int sessionId, int index) {
		super(user, sessionId,index);
		oldExp = experience = user.getExpByAppId(DBConstants.APPID_ZHAJINHUA);
		level = user.getLevelByAppId(DBConstants.APPID_ZHAJINHUA); 
		balance = user.getBalance();
		dbclient = new MongoDBClient(DBConstants.D_GAME);
	}
	
	@Override
	public void handleMessage(GameMessage message){
		
		switch (message.getCommand()){
		
			case GAME_START_NOTIFICATION_REQUEST:

				break;
			case NEXT_PLAYER_START_NOTIFICATION_REQUEST:
				if (message.getCurrentPlayUserId().equals(userId)){
					if ( betTimes <=17) {
						scheduleSend(singleBet, count, false);
						betTimes++;
					} else {
						scheduleFoldCard();
					}
				}
				break;
			case BET_REQUEST:
				if ( !message.getCurrentPlayUserId().equals(userId)){
					BetRequest request = message.getBetRequest();
					singleBet = request.getSingleBet();
					int count = request.getCount();
				}
				break;
			case CHECK_CARD_REQUEST:
				break;
			case COMPARE_CARD_REQUEST:
				break;
			case FOLD_CARD_REQUEST:
				break;
			case SHOW_CARD_REQUEST:
				break;
			default:
				break;
		}
	}

	
	private void scheduleSend(final int singleBet, final int count, final boolean isAutoBet) {
		
		if ( FoldCardTimerFuture != null  ){
			FoldCardTimerFuture.cancel(false);
		}
		
		FoldCardTimerFuture = scheduleService.schedule(new Runnable() {			
			@Override
			public void run() {
				sendBet(singleBet, count, isAutoBet);
			}
		}, 
		RandomUtils.nextInt(2)+1, TimeUnit.SECONDS);
	}
	
	private void scheduleFoldCard(){
		
		if ( FoldCardTimerFuture != null  ){
			FoldCardTimerFuture.cancel(false);
		}
		
		FoldCardTimerFuture = scheduleService.schedule(new Runnable() {			
			@Override
			public void run() {
				sendFoldCard();
			}
		}, 
		RandomUtils.nextInt(2)+1, TimeUnit.SECONDS);
	}
	
	
	private void sendBet(int singleBet, int count, boolean isAutoBet) {
		
		ServerLog.info(sessionId, "Robot "+nickName+" bets");
		
		BetRequest request = BetRequest.newBuilder()
				.setSingleBet(singleBet)
				.setCount(count)
				.setIsAutoBet(isAutoBet)
				.build();
		
		GameMessage message = GameMessage.newBuilder()
				.setBetRequest(request)
				.setMessageId(getClientIndex())
				.setCommand(GameCommandType.BET_REQUEST)
				.setUserId(userId)
				.setSessionId(sessionId)
				.build();
		
		send(message);		
	}
	
	
	private void sendFoldCard() {
		
		ServerLog.info(sessionId, "Robot "+nickName+" bets");
		
		FoldCardRequest request = FoldCardRequest.newBuilder()
				.build();
		
		GameMessage message = GameMessage.newBuilder()
				.setFoldCardRequest(request)
				.setMessageId(getClientIndex())
				.setCommand(GameCommandType.BET_REQUEST)
				.setUserId(userId)
				.setSessionId(sessionId)
				.build();
		
		send(message);
	}
	

	@Override
	public void resetPlayData(boolean robotWinThisGame) {
		betTimes = 0;
		singleBet = 5;
		count = 1;
		
	}
	
	@Override
	public String getAppId() {
		// TODO: a fake appId, you'd better not call this method.
		return DBConstants.APPID_ZHAJINHUA;
	}
	
	@Override
	public String getGameId() {
		return DBConstants.ZHAJINHUA_GAME_ID;
	}		

	@Override
	public boolean updateLevelAndExp() {
			   
		 boolean result = false;
			   // TODO
		  DBObject object = UserManager.updateLevelAndExp(dbclient, userId, DBConstants.ZHAJINHUA_GAME_ID, experience, level, true, ServiceConstant.CONST_SYNC_TYPE_UPDATE, 0);  
		  if ( object != null) {
			   result = true;
		    }
		   return result;
	}

	
	@Override
	public PBGameUser toPBGameUserSpecificPart(Builder builder) {
		return builder.build();
	}
}
