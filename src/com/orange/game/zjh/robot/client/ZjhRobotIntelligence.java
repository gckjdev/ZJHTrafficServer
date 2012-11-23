package com.orange.game.zjh.robot.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.cassandra.cli.CliParser.newColumnFamily_return;
import org.apache.commons.lang.math.RandomUtils;
import com.orange.common.log.ServerLog;
import com.orange.common.utils.IntegerUtil;
import com.orange.game.traffic.server.GameEventExecutor;
import com.orange.game.zjh.model.ZjhGameConstant;
import com.orange.game.zjh.model.ZjhGameSession;
import com.orange.network.game.protocol.message.GameMessageProtos.BetRequest;
import com.orange.network.game.protocol.message.GameMessageProtos.CompareCardResponse;
import com.orange.network.game.protocol.message.GameMessageProtos.GameStartNotificationRequest;
import com.orange.network.game.protocol.message.GameMessageProtos.ShowCardRequest;
import com.orange.network.game.protocol.model.GameBasicProtos.PBUserResult;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBPoker;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHPoker;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHUserPlayInfo;

import static com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHCardType;

public class ZjhRobotIntelligence {
	
	private static final int CHIPS[] = {5, 10, 25, 50};
	
	private int playerCount;
	private int alivePlayerCount;
	
	private String mySelfId;
	private int sessionId;
	private String nickName;
	private ZjhGameSession session;
	private int myCardType;
	private List<PBPoker> myPokers;
	private int myPokerRankMask;
	private static final int MEAN_POKER_RANK = 0x173F; // 平均值： k, 9, 8 ： 1 0111 0011 1111
	
	private Map<String, List<PBPoker>> userPokersMap = new ConcurrentHashMap<String, List<PBPoker>>();
	private Map<String, Integer> cardTypeMap = new ConcurrentHashMap<String, Integer>();

	private List<Entry<String, Integer>> cardTypeList ; 
	
	private int round = 1;
	private int lastRaiseBetRound = -1;
	
	private boolean loseGame = false;
	private boolean hasCheckedCard = false;
	private boolean hasFoldedCard = false;
	
	private static final int IDX_CHECK = 0;
	private static final int IDX_FOLD = 1;
	private static final int IDX_BET = 2;
	private static final int IDX_RAISE_BET = 3;
//	private static final int IDX_AUTO_BET = 4;
	private static final int IDX_COMPARE = 5;
	private static final int IDX_SHOW = 6;
//	private static final int IDX_ALL = 7;
	boolean[] decision = {false,false,false,false,false,false,false};

	
	private static final int BOTTOM_UP = 0;
	private static final int UPSIDE_DOWN = 1;
	
	private int singleBet ;
	private int oldSingleBet;
	private boolean raiseBet = false;
	
	private int betRaisedByOtherRound = -1;
	private boolean betRaisedByOther = false;
	
	private String toCompareUserId = null;
	private boolean compareWin = true;

	private int betTimes;
	private int random;

	private List<Integer> showCardIdList =  new ArrayList<Integer>();

	private boolean hasShowed;
	
	public ZjhRobotIntelligence(int sessionId, String userId, String nickName) {
		this.mySelfId = userId;
		this.sessionId = sessionId;
		this.nickName = nickName;
		this.session = (ZjhGameSession) GameEventExecutor.getInstance().getSessionManager().findSessionById(sessionId);
		this.singleBet = session.getSingleBet();
		this.oldSingleBet = this.singleBet;
	}


	public void introspectPokers(GameStartNotificationRequest request) {
		
		List<PBZJHUserPlayInfo> infos = request.getZjhGameState().getUsersInfoList();
		
		for ( PBZJHUserPlayInfo info: infos ) {
			String userId = info.getUserId();
			PBZJHPoker zjhPoker = info.getPokers();
			
			List<PBPoker> pbPokers = zjhPoker.getPokersList();
			int cardType = zjhPoker.getCardType().getNumber();
			
			userPokersMap.put(userId, pbPokers);
			cardTypeMap.put(userId, cardType);
			if ( userId.equals(mySelfId)) {
				myCardType = cardType;
				myPokers = zjhPoker.getPokersList();
				myPokerRankMask = pokersToRankMask(myPokers);
			} 
		}

		cardTypeList = new ArrayList<Entry<String, Integer>>(cardTypeMap.entrySet());
		Collections.sort(cardTypeList, new Comparator<Entry<String, Integer>>() {
			@Override
			public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
				return o2.getValue() - o2.getValue();  // 降序，所以用o2.getValue()-o1.getValue() !
			}
		});	
		
		playerCount = session.getPlayUserCount();
		random = RandomUtils.nextInt(ZjhGameConstant.SESSION_MAX_PLAYER_COUNT);
	}

	
	private int pokersToRankMask(List<PBPoker> myPokers) {
		
		int rankMask = ZjhGameConstant.RANK_MASK;
		for (PBPoker pbPoker : myPokers) {
			rankMask &= ~( 1 << pbPoker.getRank().ordinal());
		}
		
		return rankMask;
	}



	public boolean needToPlay() {

		if ( hasFoldedCard || loseGame )  {
			ServerLog.info(sessionId, "Robot "+nickName +" losed the game, can't play");
			return false;
		}

		ServerLog.info(sessionId, "CardType = " + myCardType +", current round = " + round+", alivePlayerCount = "+ alivePlayerCount);
		switch (myCardType) {
			case PBZJHCardType.HIGH_CARD_VALUE:
			case PBZJHCardType.SPECIAL_VALUE:
				highCardDecision();
				break;
			case PBZJHCardType.PAIR_VALUE:
				pairCardDecision();
				break;
			case PBZJHCardType.STRAIGHT_VALUE:
				straightCardDecision();
				break;
			case PBZJHCardType.FLUSH_VALUE:
				flushCardDecision();
				break;
			case PBZJHCardType.STRAIGHT_FLUSH_VALUE:
				straightFlushCardDecision();
				break;
			case PBZJHCardType.THREE_OF_A_KIND_VALUE:
				threeOfaKindCardDecision();
				break;
		}
		round++;
		
		return true;
	}

	private void threeOfaKindCardDecision() {
		
		alivePlayerCount = session.getAlivePlayerCount();
		
		if ( ! canRaiseBet() ) {
			decision[IDX_BET] = true;
			ServerLog.info(sessionId, "<threeOfaKindCardDecision> " + nickName+" decides to follow bet, singleBet is " + singleBet);
		}
		if ( round > 3 ) {
			showCard();
		}
		else if ( lastRaiseBetRound == -1 && round >= 2 + RandomUtils.nextInt(2) 
				|| playerCount - alivePlayerCount > 1 ) { 
			if (!hasCheckedCard && RandomUtils.nextInt(2) == 1 ) 
				decision[IDX_CHECK] = true;
			decision[IDX_RAISE_BET] = true;
			oldSingleBet = singleBet;
			singleBet = chooseChip(BOTTOM_UP);
			lastRaiseBetRound = round;
			raiseBet = true;
			ServerLog.info(sessionId, "<threeOfaKindCardDecision> " + nickName+" decides to raise bet");
		}
		else if ( lastRaiseBetRound != -1 && round - lastRaiseBetRound >= alivePlayerCount ) {
			// 太多人的时候加注太快容易把人吓走，但人少了(本来就少或者是有人输了，放弃了)的情况
			// 下，基本可以断定是进入“决战”阶段，所以加注吧
			if (!hasCheckedCard && RandomUtils.nextInt(2) == 1 ) 
				decision[IDX_CHECK] = true;
			decision[IDX_RAISE_BET] = true;
			oldSingleBet = singleBet;
			singleBet = chooseChip(UPSIDE_DOWN);
			lastRaiseBetRound = round;
			raiseBet = true;
			ServerLog.info(sessionId, "<threeOfaKindCardDecision> " + nickName+" decides to raise bet");
		}
		else if ( round > 10 ) {
			decision[IDX_COMPARE] = true;
			toCompareUserId = session.getComprableUserIdList(mySelfId).get(0);
			ServerLog.info(sessionId, "<threeOfaKindCardDecision> " + nickName+" decides to compare card");
		}
		else {
			decision[IDX_BET] = true;
			ServerLog.info(sessionId, "<threeOfaKindCardDecision> " + nickName+" decides to follow bet, singleBet is " + singleBet);
		}
		
	}


	private void straightFlushCardDecision() {

		alivePlayerCount = session.getAlivePlayerCount();
		
		if ( ! canRaiseBet() ) {
			decision[IDX_BET] = true;
			ServerLog.info(sessionId, "<straightFlushCardDecision> " + nickName+" decides to follow bet, singleBet is " + singleBet);
		}
		if ( round > 3 ) {
			showCard();
		}
		else if ( lastRaiseBetRound == -1 && round >= 2 + RandomUtils.nextInt(2) 
				|| playerCount - alivePlayerCount > 1 ) { 
			// 执行到playerCount - alivePlayerCount 说明
			if (!hasCheckedCard && RandomUtils.nextInt(2) == 1 ) 
				decision[IDX_CHECK] = true;
			decision[IDX_RAISE_BET] = true;
			oldSingleBet = singleBet;
			singleBet = chooseChip(BOTTOM_UP);
			lastRaiseBetRound = round;
			raiseBet = true;
			ServerLog.info(sessionId, "<straightFlushCardDecision> " + nickName+" decides to raise bet");
		}
		else if ( lastRaiseBetRound != -1 && round - lastRaiseBetRound >= alivePlayerCount ) {
			// 太多人的时候加注太快容易把人吓走，但人少了(本来就少或者是有人输了，放弃了)的情况
			// 下，基本可以断定是进入“决战”阶段，所以加注吧
			if (!hasCheckedCard && RandomUtils.nextInt(2) == 1 ) 
				decision[IDX_CHECK] = true;
			decision[IDX_RAISE_BET] = true;
			oldSingleBet = singleBet;
			singleBet = chooseChip(UPSIDE_DOWN);
			lastRaiseBetRound = round;
			raiseBet = true;
			ServerLog.info(sessionId, "<straightFlushCardDecision> " + nickName+" decides to raise bet");
		}
		else if ( round > 10 ) {
			decision[IDX_COMPARE] = true;
			toCompareUserId = session.getComprableUserIdList(mySelfId).get(0);
			ServerLog.info(sessionId, "<straightFlushCardDecision> " + nickName+" decides to compare card");
		}
		else {
			decision[IDX_BET] = true;
			ServerLog.info(sessionId, "<straightFlushCardDecision> " + nickName+" decides to follow bet, singleBet is " + singleBet);
		}
	}


	private void flushCardDecision() {
		
		alivePlayerCount = session.getAlivePlayerCount();
		

		if ( round > 2 ) {
			showCard();
		}
		if ( (round > 8  && alivePlayerCount ==2) || round > 1 &&  playerCount > 2 && playerCount-alivePlayerCount >= 2 ) {
			// 只针对四，或五个人的情况
			if ( !hasCheckedCard ) 
				decision[IDX_CHECK] = true;
			decision[IDX_COMPARE] = true;
			toCompareUserId = session.getComprableUserIdList(mySelfId).get(0);
			ServerLog.info(sessionId, "<flushCardDecision> " + nickName+" decides to compare card");
		} 
		else if ( lastRaiseBetRound == -1 && round >= 2 + RandomUtils.nextInt(2) 
				|| playerCount - alivePlayerCount > 1 ) {
			if (!hasCheckedCard && RandomUtils.nextInt(2) == 1 ) 
				decision[IDX_CHECK] = true;
			decision[IDX_RAISE_BET] = true;
			oldSingleBet = singleBet;
			singleBet = chooseChip(BOTTOM_UP);
			lastRaiseBetRound = round;
			raiseBet = true;
			ServerLog.info(sessionId, "<flushCardDecision> " + nickName+" decides to raise bet");
		}
		else {
			decision[IDX_BET] = true;
			ServerLog.info(sessionId, "<flushCardDecision> " + nickName+" decides to follow bet, singleBet is " + singleBet);
		}
		
	}


	private void straightCardDecision() {
		
		alivePlayerCount = session.getAlivePlayerCount();

		
		if ( round > 3 ) {
			showCard();
		}
		if ( round > 1 && playerCount > 2 && playerCount-alivePlayerCount >= 2 ) {
			// 只针对四，或五个人的情况
			if (!hasCheckedCard)
				decision[IDX_CHECK] = true;
			decision[IDX_COMPARE] = true;
			toCompareUserId = session.getComprableUserIdList(mySelfId).get(0);
			ServerLog.info(sessionId, "<straightCardDecision> " + nickName+" decides to compare card");
		}
		else if ( canRaiseBet() && ! betRaisedByOther &&  round - lastRaiseBetRound > 2) {
			if (!hasCheckedCard && RandomUtils.nextInt(2) == 1 ) 
				decision[IDX_CHECK] = true;
			decision[IDX_RAISE_BET] = true;
			oldSingleBet = singleBet;
			singleBet = chooseChip(BOTTOM_UP);
			lastRaiseBetRound = round;
			raiseBet = true;
			ServerLog.info(sessionId, "<pairCardDecision> " + nickName+" decides to raise bet");
		}
		else if ( round > playerCount + RandomUtils.nextInt(3) && betRaisedByOther ){
			if (!hasCheckedCard) 
				decision[IDX_CHECK] = true;
			decision[IDX_COMPARE] = true;
			toCompareUserId = session.getComprableUserIdList(mySelfId).get(0);
			ServerLog.info(sessionId, "<straightCardDecision> " + nickName+" decides to compare card(2)");
		} else {
			decision[IDX_BET] = true;
			ServerLog.info(sessionId, "<straightCardDecision> " + nickName+" decides to follow bet, singleBet is " + singleBet);
		}
		
	}


	private void pairCardDecision() {

		alivePlayerCount = session.getAlivePlayerCount();
		
		if ( round >= 3 && !hasCheckedCard ) {
			decision[IDX_CHECK] = true;
			hasCheckedCard = true;
		} else if ( round < 3 && RandomUtils.nextInt(alivePlayerCount) == 1 && !hasCheckedCard) {
			decision[IDX_CHECK] = true;
			hasCheckedCard = true;
		}
		
		if ( round > 3 ) {
			showCard();
		}
		
		if ( playerCount <= 3 ) {
			if ( round > 5 && alivePlayerCount == 2 || RandomUtils.nextInt(2) == 0) {
				decision[IDX_COMPARE] = true;
				toCompareUserId = session.getComprableUserIdList(mySelfId).get(0);
				ServerLog.info(sessionId, "<pairCardDecision> " + nickName+" decides to compare card");
			} else if ( !raiseBet && canRaiseBet() && round <= 2 + RandomUtils.nextInt(alivePlayerCount)){
				if (!hasCheckedCard && RandomUtils.nextInt(2) == 1 ) 
					decision[IDX_CHECK] = true;
				decision[IDX_RAISE_BET] = true;
				oldSingleBet = singleBet;
				singleBet = chooseChip(BOTTOM_UP);
				lastRaiseBetRound = round;
				raiseBet = true;
				ServerLog.info(sessionId, "<pairCardDecision> " + nickName+" decides to raise bet");
			} else {
				decision[IDX_BET] = true;
				ServerLog.info(sessionId, "<pairCardDecision> " + nickName+" decides to follow bet, singleBet is " + singleBet);
			}
		} else {
			if ((round > 2 && playerCount - alivePlayerCount >= 2) || ( round > alivePlayerCount && betRaisedByOther) || round > 5 + RandomUtils.nextInt(2)) {
				decision[IDX_COMPARE] = true;
				toCompareUserId = session.getComprableUserIdList(mySelfId).get(0);
				ServerLog.info(sessionId, "<pairCardDecision> " + nickName+" decides to raise bet(2)");
			} else {
				decision[IDX_BET] = true;
				ServerLog.info(sessionId, "<pairCardDecision> " + nickName+" decides to follow bet(2), singleBet is " + singleBet);
			} 
		}
		
	}

	private void highCardDecision() {
		
		alivePlayerCount = session.getAlivePlayerCount();
		 
		if ( round >= 3 && !hasCheckedCard ) {
			decision[IDX_CHECK] = true;
			hasCheckedCard = true;
		} else if ( round < 3 && RandomUtils.nextInt(alivePlayerCount) == 1 && !hasCheckedCard) {
			decision[IDX_CHECK] = true;
			hasCheckedCard = true;
		}

		if ( round > 3 ) {
			showCard();
		}
		
		int highRankCardPos = IntegerUtil.forPosition(myPokerRankMask, 0x1FFF, 0, 0);
		if ( highRankCardPos < 9) {
			if ( random == 0 && betTimes < 3 + RandomUtils.nextInt(2)) {
				decision[IDX_BET] = true;
				ServerLog.info(sessionId, "<highCardDecision> " + nickName+" decides to follow bet, singleBet is " + singleBet
						+ ", betTimes = " + betTimes);
				betTimes++;
			} else if (RandomUtils.nextInt(alivePlayerCount) == 0) {
				decision[IDX_CHECK] = true;
				decision[IDX_FOLD] = true;
				ServerLog.info(sessionId, "<highCardDecision> " + nickName+" decides to fold card");
			} else {
				if (!hasCheckedCard)
					decision[IDX_CHECK] = true;
				decision[IDX_COMPARE] = true;
				toCompareUserId = session.getComprableUserIdList(mySelfId).get(0);
				ServerLog.info(sessionId, "<highCardDecision> " + nickName+" decides to compare card");
			}
		}
		else if ( myPokerRankMask < MEAN_POKER_RANK ) {
			// 比均值牌大
			if ( round >= 5 && betRaisedByOther || playerCount - alivePlayerCount >= 1) {
				if (!hasCheckedCard)
					decision[IDX_CHECK] = true;
				decision[IDX_COMPARE] = true;
				toCompareUserId = session.getComprableUserIdList(mySelfId).get(0);
				ServerLog.info(sessionId, "<highCardDecision> " + nickName+" decides to compare card(2)");
			} else {
				decision[IDX_BET] = true;
				ServerLog.info(sessionId, "<highCardDecision> " + nickName+" decides to follow bet(2), singleBet is " + singleBet);
			}
		}
		else {
			// 小于均值牌，最大牌又大于9的情况
			if ( betTimes < (4 - playerCount) + 4 || (compareWin && RandomUtils.nextInt(2) == 0) || round > 10) {
				decision[IDX_BET] = true;
				betTimes++;
				ServerLog.info(sessionId, "<highCardDecision> " + nickName+" decides to follow bet(3), singleBet is " + singleBet);
			} else {
				if (!hasCheckedCard)
					decision[IDX_CHECK] = true;
				decision[IDX_COMPARE] = true;
				toCompareUserId = session.getComprableUserIdList(mySelfId).get(0);
				ServerLog.info(sessionId, "<highCardDecision> " + nickName+" decides to compare card(3)");
			}
		}

	}


	private boolean canRaiseBet() {
		if ( session.getSingleBet() < CHIPS[CHIPS.length-1] )
			return true;
		return false;
	}


	// direction决定选择筹码的方向, BOTTOMUP表示从最小的开始，UPSIDEDOWN则相反。
	private int chooseChip(int direction) {
		int currentSingleBet = singleBet;
		int i = 0; 
		
		for ( ; i < CHIPS.length; i++ ) 
			if ( CHIPS[i] >  currentSingleBet )
				break;
		
		if ( i == CHIPS.length ) {
			return CHIPS[i-1];
		}
		else if ( direction == BOTTOM_UP ) {
			ServerLog.info(sessionId, "!!!!!Current single bet is " +currentSingleBet+", bottomup, raise bet to " + CHIPS[i]);
			return CHIPS[i];
		}
		else {
			ServerLog.info(sessionId, "!!!!!Current single bet is " +currentSingleBet+", upsidedown, raise bet to " + CHIPS[CHIPS.length-1]);
			return CHIPS[CHIPS.length-1];
		}
			
	}


	private void showCard() {
		
		if ( RandomUtils.nextInt(8) == 0 && ! hasShowed ) {
			
			if (!hasCheckedCard && RandomUtils.nextInt(2) == 1 ) 
				decision[IDX_CHECK] = true;
			
			int random = RandomUtils.nextInt(ZjhGameConstant.PER_USER_CARD_NUM);
			showCardIdList.add(myPokers.get(random).getPokerId());
			decision[IDX_SHOW] = true;
			hasShowed = true;
		}
		
	}
	
	
	public void handleCompareResponse(CompareCardResponse response) {
		
		for ( PBUserResult result:response.getUserResultList() ) {
			if (result.getUserId().equals(mySelfId)) {
				if ( result.getWin() == false ) {
					compareWin = false;
					loseGame = true;
					break;
				} else {
					compareWin = true;
					break;
				}
			}
		}
	}


	public void handleBetRequest(String userId, BetRequest betRequest) {
		
		singleBet = betRequest.getSingleBet();
		if ( oldSingleBet < singleBet ) {
			betRaisedByOther = true;
			betRaisedByOtherRound = round;
			ServerLog.info(sessionId, "Single bet raised from " + oldSingleBet +" to " + singleBet);
		}
		
	}


	public int getSingleBet() {
		return singleBet;
	}


	public boolean hasFoldedCard() {
		return hasFoldedCard;
	}


	public boolean hasCheckedCard() {
		return hasCheckedCard;
	}


	public String getCompareToUserId() {
		return toCompareUserId;
	}


	public void resetPlayData() {

		round = 1;
		lastRaiseBetRound = -1;
		betTimes = 0;
		
		loseGame = false;
		hasCheckedCard = false;
		hasFoldedCard = false;
		hasShowed = false;
		
		singleBet = session.getSingleBet();
		oldSingleBet = singleBet;
		raiseBet = false;
		
		betRaisedByOtherRound = -1;
		betRaisedByOther = false;
		
		toCompareUserId = null;
		compareWin = true;
		
		myCardType = 0;
		myPokerRankMask = 0;

		showCardIdList.clear();
		
		
		if (showCardIdList != null){
			showCardIdList.clear();
		}
		
	}


	public void cleanDecision() {
		
		for ( int i = 0; i < decision.length; i++ ) {
			decision[i] = false;
		}
	}


	
	public boolean canCheckCardNow() {
		
		if ( myCardType >= PBZJHCardType.STRAIGHT_VALUE )
			return true;
		else 
			return false;
	}


	public void setHasCheckedCard() {
		hasCheckedCard = true;
	}


	
	public void setFoldedCard() {

		hasFoldedCard = true;
		loseGame = true;
	}

	

	public List<Integer> getShowCardIds() {
		return showCardIdList; 
	}
	
	
}
