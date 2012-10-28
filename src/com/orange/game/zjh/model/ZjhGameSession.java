package com.orange.game.zjh.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.lang.math.RandomUtils;

import com.orange.common.log.ServerLog;
import com.orange.common.utils.IntegerUtil;
import com.orange.game.traffic.model.dao.GameSession;
import com.orange.game.traffic.model.dao.GameUser;
import com.orange.game.zjh.statemachine.ZjhGameStateMachineBuilder;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBPoker;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBPokerRank;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBPokerSuit;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHCardType;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHPoker;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHUserAction;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHUserInfo;


public class ZjhGameSession extends GameSession {
	
	// to store per-user card info
	private List<PBZJHUserInfo> userPokerInfoList = new CopyOnWriteArrayList<PBZJHUserInfo>();
	// to store the shuffled pokers
	private List<PBPoker> pbPokers = new ArrayList<PBPoker>();
	// auxiliary poker pool
	private int[] pokers = new int[ZjhGameConstant.ALL_CARD_NUM];
	private int singleBet;
	private int totalBet;
	
	public ZjhGameSession(int sessionId, String name, String password, boolean createByUser, String createBy, int ruleType,int testEnable) {
		super(sessionId, name, password, createByUser, createBy, ruleType, testEnable);
		// init state
		this.currentState = ZjhGameStateMachineBuilder.INIT_STATE;
		for (int i = 0; i < ZjhGameConstant.ALL_CARD_NUM; i++) {
			pokers[i] = i;
		}
		this.singleBet = getSingleBet();
		this.totalBet =  this.singleBet * getPlayUserCount();
	}
	

	public void resetGame(){
		super.resetGame();
	}
	
	@Override	
	public void restartGame(){	
		//清空totalBet, pokers
	}

	public int getAlivePlayerCount() {
		// TODO Auto-generated method stub
		return 0;
	}


	public int getTotalBet() {
		return totalBet;
	}



	public int getSingleBet() {
//		int ruleType = getRuleType();
		return 0;
	}

	public List<PBZJHUserInfo> getUserCardInfo() {
		return userPokerInfoList;
	}


	public List<PBZJHUserInfo> deal() {
		
		List<GameUser> gameUsers = getUserList().getPlayingUserList();
		
		// shuffle pokers first of course
		shufflePokers();
		
		for(GameUser user : gameUsers) {
			String userId = user.getUserId();
			List<PBPoker> pokers = dispatchPokers();
			PBZJHCardType cardType = introspectCardType(pokers);
			
			PBZJHPoker pbZjhPoker = PBZJHPoker.newBuilder()
												.addAllPokers(pokers)
												.setCardType(cardType)
												.build();
			ServerLog.info(sessionId, "<deal> User "+userId+" gets pokers: " + pokers.toString()
					+", cardType is " + cardType);
			
			int perTotalBet = getSingleBet(); // at beginning it eqautes to singlebet.
			boolean isAutoBet = false;
			PBZJHUserAction lastAction = PBZJHUserAction.NONE;
			
			boolean hasCheckedCard = false;
			boolean hasFoldedCard = false;
			boolean hasShowedCard = false;
			boolean loseTheGame = false;
			
			PBZJHUserInfo uesrCardInfo = PBZJHUserInfo.newBuilder()
								.setUserId(userId)
								.setPokers(pbZjhPoker)
								.setTotalBet(perTotalBet)
								.setIsAutoBet(isAutoBet)
								.setLastAction(lastAction)
								.setAlreadCheckCard(hasCheckedCard)
								.setAlreadFoldCard(hasFoldedCard)
								.setAlreadShowCard(hasShowedCard)
								.setAlreadLose(loseTheGame)
								.build();
			
			userPokerInfoList.add(uesrCardInfo);
			totalBet  += perTotalBet;
		}
		return userPokerInfoList;
	}

	private void shufflePokers() {
		
		for (int i = ZjhGameConstant.ALL_CARD_NUM - 1; i > 0; i--) {
			// do the real shuffle job
			int random = RandomUtils.nextInt(i);
			int tmp = pokers[random];
			pokers[random] = pokers[i];
			pokers[i] = tmp;
			
			// translate int to PBPoker
			// TODO : Actually, we need at most playerCount * PER_USER_CARD_NUM cards, so may needn't translate them all.
			PBPokerRank rank = PBPokerRank.valueOf(pokers[i] / 4 + 2);
			PBPokerSuit suit = PBPokerSuit.valueOf(pokers[i] % 4 + 1);
			int pokerId =  toPokerId(rank, suit);
			boolean faceup = false;
			
			PBPoker pbPoker = PBPoker.newBuilder()
										.setPokerId(pokerId)
										.setRank(rank)
										.setSuit(suit)
										.setFaceUp(faceup)
										.build();
			
			pbPokers.add(pbPoker);
		}
		ServerLog.info(sessionId, "<shufflePokers> Pokers shuffled!!!");
	}
	
	private List<PBPoker> dispatchPokers() {
		
		List<PBPoker> result = new ArrayList<PBPoker>();
		
		for (int i = 0; i < ZjhGameConstant.PER_USER_CARD_NUM; i++) {
			PBPoker tmp = pbPokers.remove(0);
			result.add(tmp);
		}
		return result;
	}

	
	private PBZJHCardType introspectCardType(List<PBPoker> pokers) {
		
		PBZJHCardType type = null;
		int[] ranks = new int[ZjhGameConstant.PER_USER_CARD_NUM];
		int[] suits = new int[ZjhGameConstant.PER_USER_CARD_NUM];
		
		int rankMask = ZjhGameConstant.RANK_MASK;
		int suitMask = ZjhGameConstant.SUIT_MASK;
		
		for (int i = 0; i < ZjhGameConstant.PER_USER_CARD_NUM; i++) {
			PBPoker poker = pokers.get(i);
			if ( poker == null ) {
				ServerLog.error(sessionId, new NullPointerException());
				return PBZJHCardType.UNKNOW;
			}
			ranks[i] = poker.getRank().ordinal();
			suits[i] = poker.getSuit().ordinal();
			
			rankMask &= ~( 1 << ranks[i]);
			suitMask &= ~( 1 << suits[i]);
		}
		
		int howManyRanks = ZjhGameConstant.PER_SUIT_NUM - IntegerUtil.howManyOneBit(rankMask);
		int howManySuits = ZjhGameConstant.SUIT_TYPE_NUM - IntegerUtil.howManyOneBit(suitMask);
		boolean hasThreeConsecutiveBit = IntegerUtil.hasConsecutiveBit(rankMask, 3, 0);
		
		if ( rankMask == ZjhGameConstant.TYPE_SPECIAL ) { 
			type = PBZJHCardType.SPECIAL; // 最大的牌，2,3,5
		}
		else if ( howManyRanks == 3 && ! hasThreeConsecutiveBit) {
			type = PBZJHCardType.HIGH_CARD; // 散牌
		} 
		else if ( howManyRanks == 2 ) {
			type = PBZJHCardType.PAIR;  // 对子
		}
		else if ( hasThreeConsecutiveBit ) {
			if ( howManySuits == 1 ) {
				type = PBZJHCardType.STRAIGHT_FLUSH; // 顺金
			} else {
				type = PBZJHCardType.STRAIGHT; // 顺子
			}
		}
		else if ( howManySuits == 1) {
			type = PBZJHCardType.FLUSH; // 金花
		}
		else if ( howManyRanks == 1 ) {
			type = PBZJHCardType.THREE_OF_A_KIND; // 豹子
		} else {
			type = PBZJHCardType.UNKNOW; // 未知
		}
		
		return type;
	}

	private int toPokerId(PBPokerRank rank, PBPokerSuit suit) {
		
		// id start from 0
		int id = 0;
		id = ( rank.getNumber() - 2 ) * ZjhGameConstant.SUIT_TYPE_NUM + suit.getNumber() - 1;
		
		return id;
	}


//	public List<PBZJHUserPoker> getDealResult() {
//		
//		List<PBZJHUserPoker> result = new ArrayList<PBZJHUserPoker>();
//		
//		// do deal job
//		deal();
//		
//		for (PBZJHUserInfo userInfo: userPokerInfoList) {
//			String userId = userInfo.getUserId();
//			PBZJHPoker pokers = userInfo.getPokers();
//			PBZJHUserPoker userPoker = PBZJHUserPoker.newBuilder()
//												.setUserId(userId)
//												.setPokers(pokers)
//												.build();
//			result.add(userPoker);
//		}
//		return result;
//	}
	
}