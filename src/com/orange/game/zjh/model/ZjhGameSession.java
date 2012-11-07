package com.orange.game.zjh.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang.math.RandomUtils;
import com.orange.common.log.ServerLog;
import com.orange.common.utils.IntegerUtil;
import com.orange.game.constants.DBConstants;
import com.orange.game.model.manager.UserManager;
import com.orange.game.traffic.model.dao.GameSession;
import com.orange.game.traffic.model.dao.GameUser;
import com.orange.game.traffic.model.manager.GameUserManager;
import com.orange.game.zjh.statemachine.ZjhGameStateMachineBuilder;
import com.orange.network.game.protocol.constants.GameConstantsProtos.GameResultCode;
import com.orange.network.game.protocol.model.GameBasicProtos.PBUserResult;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBPoker;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBPokerRank;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBPokerSuit;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHCardType;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHPoker;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHUserAction;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHUserPlayInfo;

// static import~
import static com.orange.game.zjh.model.ZjhGameConstant.*;
import static com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHCardType.*;

public class ZjhGameSession extends GameSession {
	
	// 扑克堆
	private int[] pokerPool = new int[ZjhGameConstant.ALL_CARD_NUM];
	private int pokerPoolCursor = 0;
	
	// 指未弃牌，游戏未输，游戏过程中没退出的玩家个数
	private AtomicInteger alivePlayerCount = new AtomicInteger(0);
	// 房间当前单注值， 默认值跟房间类型有关
	private int singleBet;
	// 房间当前总注
	private int totalBet;
	
	// 每个玩家的扑克
	private Map<String, List<PBPoker>> userPokersMap = new ConcurrentHashMap<String, List<PBPoker>>();
	// 每个玩家的牌型数据结构
	private Map<String, PBZJHCardType> cardTypeMap = new ConcurrentHashMap<String, PBZJHCardType>();
	// 每个玩家的牌面值和花色值信息, 与userPokersMap有重复。方便比牌时操作
	private Map<String, Integer> rankMaskMap = new ConcurrentHashMap<String, Integer>();
	private Map<String, Integer> suitMaskMap = new ConcurrentHashMap<String, Integer>();
	// 每个玩家的扑克是否亮牌， 方便亮牌操作
	private Map<String, Integer> faceStatusMap = new ConcurrentHashMap<String, Integer>();
	// 每个玩家的总注
	private Map<String, Integer> totalBetMap =  new ConcurrentHashMap<String, Integer>();
		
	// 每玩家游戏状态信息
	// bit 0 : is auto bet?  [0: false, 1: true]
	// bit 1 : has checked card? [0: false, 1: true]
	// bit 2 : has folded card? [0: false, 1: true]
	// bit 3 : has showed card? [0: false, 1: true]
	// bit 4 : has losed the game? [0: false, 1: true]
	// --- from bit 5 to bit 13, is to store per-user's last action
	// bit 5 : none
	// bit 6 : bet
	// bit 7 : raise bet
	// bit 8 : auto bet
	// bit 9 : check card
	// bit 10 : fold card
	// bit 11 : compare card
	// bit 12 : show card
	// bit 13 : change card
	// *** so the initial value of userInfoMask is 0x20 (0 0000 0010 0000) 
	private Map<String, Integer> userPlayInfoMask = new ConcurrentHashMap<String, Integer>();


	
	public ZjhGameSession(int sessionId, String name, String password, boolean createByUser, String createBy, int ruleType,int testEnable) {
		super(sessionId, name, password, createByUser, createBy, ruleType, testEnable);
		// init state
		this.currentState = ZjhGameStateMachineBuilder.INIT_STATE;
		for (int i = 0; i < ZjhGameConstant.ALL_CARD_NUM; i++) {
			pokerPool[i] = i;
		}
		this.singleBet = getSingleBet();
	}
	

	public void resetGame(){
		super.resetGame();
	}
	
	
	@Override	
	public void restartGame(){	
		totalBet = 0;
		pokerPoolCursor = 0;
		userPokersMap.clear();
		cardTypeMap.clear();
		rankMaskMap.clear();
		suitMaskMap.clear();
		faceStatusMap.clear();
		totalBetMap.clear();
		userPlayInfoMask.clear();
	}

	
	public int getAlivePlayerCount() {
		ServerLog.info(sessionId, "alivePlayerCount= "+ alivePlayerCount);
		return alivePlayerCount.get();
	}


	public int getTotalBet() {
		return totalBet;
	}


	public int getSingleBet() {
//		TODO : by ruleType
//		int ruleType = getRuleType();
		return 5;
	}
	
	
	// 开局，发送StartGameNotification时调用此方法
	public List<PBZJHUserPlayInfo> deal() {
		
		List<PBZJHUserPlayInfo> result = new ArrayList<PBZJHUserPlayInfo>();
		List<GameUser> gameUsers = getUserList().getAllUsers();
		
 		// 先洗牌 !
		shufflePokers();
		 
		// 给每个玩家发牌，并构造消息以便返回
		for(GameUser user : gameUsers) {
			String userId = user.getUserId();
			List<PBPoker> pokers = dispatchPokers();
			// 按牌面值大小排序，以方便后续比牌操作, 存起备用
			Collections.sort(pokers, new Comparator<PBPoker>() {
				@Override
				public int compare(PBPoker p1, PBPoker p2) {
					int p1Val = p1.getRank().getNumber();
					int p2Val = p2.getRank().getNumber();
								
					return p2Val - p1Val;  // 降序，所以用p2-p1 !
				}
			});	
			userPokersMap.put(userId, pokers);

			//检查牌类型，存起备用
			PBZJHCardType cardType = introspectCardType(pokers,userId);
			cardTypeMap.put(userId, cardType);
			
			ServerLog.info(sessionId, "<deal> User "+userId+" gets pokers: " + pokers.toString()
					+", cardType is " + cardType);
			
			int perTotalBet = getSingleBet(); // 一开局等于单注
			totalBetMap.put(userId, perTotalBet);
			
			userPlayInfoMask.put(userId, ZjhGameConstant.USER_INFO_INITIAL_VALUE);
			
			result.add(updateUserPlayInfo(userId));
		}
		
		return result;
	}


	private void shufflePokers() {

		// 洗牌！
		// 前(ALL_CARD_NUM - 1)张中随机一张跟最后一张交换，然后，前(ALL_CARD_NUM - 2)张中随机一张跟倒数第二张
		// 交换，etc.这样，就可以把牌洗得很乱。
		for (int i = ZjhGameConstant.ALL_CARD_NUM - 1; i > 0; i--) {
			int random = RandomUtils.nextInt(i);
			int tmp = pokerPool[random];
			pokerPool[random] = pokerPool[i];
			pokerPool[i] = tmp;
		}

		ServerLog.info(sessionId, "<shufflePokers> Pokers shuffled!!!");
	}
	
	
	// 针对一个扑克堆操作，需加synchronized
	private synchronized List<PBPoker> dispatchPokers() {
		
		List<PBPoker> result = new ArrayList<PBPoker>();
		PBPokerRank rank = null;
		PBPokerSuit suit = null;
		PBPoker pbPoker = null;
		int  oldCursor = pokerPoolCursor;
		
		ServerLog.info(sessionId, "<dispatchPokers> pokerPoolCursor = " +pokerPoolCursor);
		for (int i = oldCursor; i < oldCursor + ZjhGameConstant.PER_USER_CARD_NUM; i++) {
			rank = PBPokerRank.valueOf(pokerPool[i] / 4 + 2);
			suit = PBPokerSuit.valueOf(pokerPool[i] % 4 + 1);
			int pokerId =  toPokerId(rank, suit);
			boolean faceup = false;
					
			pbPoker = PBPoker.newBuilder()
								.setPokerId(pokerId)
								.setRank(rank)
								.setSuit(suit)
								.setFaceUp(faceup)
								.build();
			result.add(pbPoker);		
			pokerPoolCursor++; // 扑克堆游标，游标前面的表示已经发出的牌, 每发出一张牌就把游标前移一个元素。
		}
		
		return result;
	}

	
	private PBZJHCardType introspectCardType(List<PBPoker> pokers,String userId) {
		
		PBZJHCardType type = null;
		int[] ranks = new int[ZjhGameConstant.PER_USER_CARD_NUM];
		int[] suits = new int[ZjhGameConstant.PER_USER_CARD_NUM];
		
		int rankMask = ZjhGameConstant.RANK_MASK;
		int suitMask = ZjhGameConstant.SUIT_MASK;
		int faceStatusMask = 0;
		
		for (int i = 0; i < ZjhGameConstant.PER_USER_CARD_NUM; i++) {
			PBPoker poker = pokers.get(i);
			if ( poker == null ) {
				ServerLog.error(sessionId, new NullPointerException());
				return PBZJHCardType.UNKNOW;
			}
			// 获得序号值来作为牌面在掩码中的第几位，比如A的ordinal是12, 即对应
			// 掩码中的第12位（从0开始计数）; 花色亦同。
			// 牌面： 1 1111 1111 1111， 从左到右分别对应: A KQJ10 9876 5432
			// 花色： 1111， 从左到右分别对应： 黑桃，红心，梅花，方块
			ranks[i] = poker.getRank().ordinal();
			suits[i] = poker.getSuit().ordinal();
			
			// 根据ordinal值把掩码中对应位置为0
			rankMask &= ~( 1 << ranks[i]);
			suitMask &= ~( 1 << suits[i]);
			// faceStatusMask为玩家牌面是否亮出，0为盖牌，1为亮牌
			// rankMask对应位的值即表示该张牌是否亮出，其他位无意义。
			faceStatusMask = rankMask; 
		}
		
		// 以备后用
		rankMaskMap.put(userId, rankMask);
		suitMaskMap.put(userId, suitMask);
		faceStatusMap.put(userId, faceStatusMask);
		
		// 有几种牌面值, 表示为牌面掩码有几个0。0x1FFF表示只考虑rankMask的低13位，即AKQJ,10-2, 共13种牌面值。
		int howManyRanks = IntegerUtil.howManyBits(rankMask, 0x1FFF, 0);
		// 有几种花色花色, 表示为花色掩码有几个0。0xF表示只考虑suitMask低4位，即四种花色。
		int howManySuits = IntegerUtil.howManyBits(suitMask,0xF, 0);
		// 牌面值掩码有没有连续的3个0, 有的话表示是顺子牌。0x1FFF意义同前。 
		boolean hasThreeConsecutiveBit = IntegerUtil.hasConsecutiveBit(rankMask, 0x1FFF,  3, 0);
		
		if ( rankMask == ZjhGameConstant.TYPE_SPECIAL ) { 
			type = PBZJHCardType.SPECIAL; // 最大的牌，2,3,5， 掩码值为 1 1111 1111 0100
		}
		else if ( howManyRanks == 3 && ! hasThreeConsecutiveBit) {
			type = PBZJHCardType.HIGH_CARD; // 散牌， 有三种牌面值，且不是连续的
		} 
		else if ( howManyRanks == 2 ) {
			type = PBZJHCardType.PAIR;  // 对子， 有二种牌面值
		}
		else if ( hasThreeConsecutiveBit ) { // 是连续的三种牌面值
			if ( howManySuits == 1 ) {
				type = PBZJHCardType.STRAIGHT_FLUSH; // 顺金， 只有一种花色
			} else {
				type = PBZJHCardType.STRAIGHT; // 顺子，不只一种花色
			}
		}
		else if ( howManySuits == 1) {
			type = PBZJHCardType.FLUSH; // 金花，即同花，只有一种花色
		}
		else if ( howManyRanks == 1 ) {
			type = PBZJHCardType.THREE_OF_A_KIND; // 豹子，同一种牌面值
		} else {
			type = PBZJHCardType.UNKNOW; // 未知
		}
		
		return type;
	}

	
	private int toPokerId(PBPokerRank rank, PBPokerSuit suit) {
		
		// id start from 0
		int id = 0;
		int rankVal = rank.getNumber();
		int suitVal = suit.getNumber();
		
		// call the overloaded method.
		// see protocol buffer definition for why minusing 2 and minusing 1
		id = (rankVal-2) * ZjhGameConstant.SUIT_TYPE_NUM + (suitVal-1);
		
		return id;
	}


	private PBPokerRank pokerIdToRank(int pokerId) {
		
		// see protocol buffer for why plusing 2
		int value = pokerId / ZjhGameConstant.SUIT_TYPE_NUM + 2;
		
		return PBPokerRank.valueOf(value);
	}

	
	private PBPokerSuit pokerIdToSuit(int pokerId) {
		
		// see protocol buffer for why plusing 1
		int value = pokerId % ZjhGameConstant.SUIT_TYPE_NUM + 1;
		
		return PBPokerSuit.valueOf(value);
	}
	
	public GameResultCode bet(String userId, int givenSingleBet, int count,
			boolean isAutoBet) {
		
		if ( !userPlayInfoMask.containsKey(userId) ) {
			ServerLog.info(sessionId, "<ZjhGameSessuion.bet> "+ userId+ " not in this session???!!!");
			return GameResultCode.ERROR_USER_NOT_IN_SESSION;
		} 
		else {
			// Update the single bet.
			int oldSingleBet = singleBet;
			singleBet = givenSingleBet;
					
			// Update his/her total bet.
			int tmp = totalBetMap.get(userId);
			totalBetMap.put(userId, tmp + givenSingleBet * count);
				
			int oldValue = userPlayInfoMask.get(userId);
			// Clear the last action first.
			oldValue &= ~LAST_ACTION_MASK;
			// Decide which bet type: bet(跟注), raise bet(加注), auto bet(自动跟注)
			if ( isAutoBet ) {
					// 更新lastAction, 并置状态为AUTO_BET
					userPlayInfoMask.put(userId, oldValue | USER_INFO_ACTION_AUTO_BET | USER_INFO_AUTO_BET);
			} else if ( oldSingleBet < givenSingleBet ) {
					userPlayInfoMask.put(userId, oldValue | USER_INFO_ACTION_RAISE_BET);
			} else { 
					userPlayInfoMask.put(userId, oldValue | USER_INFO_ACTION_BET);
			}
		}
		return GameResultCode.SUCCESS;
	}


	public GameResultCode checkCard(String userId) {
		
		if ( !userPlayInfoMask.containsKey(userId) ) {
			ServerLog.info(sessionId, "<ZjhGameSessuion.checkCard> "+ userId+ " not in this session???!!!");
			return GameResultCode.ERROR_USER_NOT_IN_SESSION;
		} 
		else {
			int userInfo = userPlayInfoMask.get(userId);
			if ( (userInfo & USER_INFO_CHECKED_CARD) == USER_INFO_CHECKED_CARD ) {
				ServerLog.info(sessionId, "<ZjhGameSessuion.checkCard> "+ userId+ "has checked card !!! Needn't recheck ");
				return GameResultCode.ERROR_ALREADY_CHECK_CARD;
			} 
			else {
				int oldValue = userPlayInfoMask.get(userId);
				// Clear the last action first.
				oldValue &= ~LAST_ACTION_MASK;
				userPlayInfoMask.put(userId, oldValue | USER_INFO_ACTION_CHECK_CARD | USER_INFO_CHECKED_CARD);
			}
		}
		return GameResultCode.SUCCESS;
	}

	
	public synchronized GameResultCode foldCard(String userId) {
		
		if ( !userPlayInfoMask.containsKey(userId) ) {
			ServerLog.info(sessionId, "<ZjhGameSessuion.foldCard> "+ userId+ " not in this session???!!!");
			return GameResultCode.ERROR_USER_NOT_IN_SESSION;
		} 
		else {
			int userInfo = userPlayInfoMask.get(userId);
			if ( (userInfo & USER_INFO_FOLDED_CARD) == USER_INFO_FOLDED_CARD ) {
				ServerLog.info(sessionId, "<ZjhGameSessuion.foldCard> "+ userId+ "has folded card !!! Can not fold again");
				return GameResultCode.ERROR_ALREADY_FOLD_CARD;
			}
			else {
				int oldValue = userPlayInfoMask.get(userId);
				// Clear the last action first.
				oldValue &= ~LAST_ACTION_MASK;
				userPlayInfoMask.put(userId, oldValue | USER_INFO_ACTION_FOLD_CARD | USER_INFO_FOLDED_CARD | USER_INFO_LOSED_GAME);
				// 递减存活玩家个数
				alivePlayerCount.decrementAndGet(); // he/she is game over
				// 把玩家loseGame状态设为true，以免在selectPlayUser时再被选择到(弃牌后该玩家游戏结束)。
				GameUserManager.getInstance().findUserById(userId).setLoseGame(true);
				
				ServerLog.info(sessionId, "After "+userId+" folding card, alivePlayerCount= " + alivePlayerCount);
			}
		}
		return GameResultCode.SUCCESS;
	}


	public GameResultCode showCard(String userId, List<Integer> pokerIds) {
		
		if ( !userPlayInfoMask.containsKey(userId) ) {
			ServerLog.info(sessionId, "<ZjhGameSessuion.showCard> "+ userId+ " not in this session???!!!");
			return GameResultCode.ERROR_USER_NOT_IN_SESSION;
		} 
		else {
			
			// 亮牌.
			int faceStatusMask = faceStatusMap.get(userId);
			int rankMask = rankMaskMap.get(userId);
			for ( Integer showPokerId : pokerIds ) {
				int rank = pokerIdToRank(showPokerId).ordinal();
				faceStatusMask |= (1 << rank); // 把对应位置为1, 表示该牌亮出来了
				ServerLog.info(sessionId, userId + " shows card : " + PBPokerRank.valueOf(rank+2)+"[suit:" + pokerIdToSuit(showPokerId)+"]" );
			}
			// 亮完牌， 把其他无意义位置0,以免混淆
			faceStatusMask &= ~rankMask;
			faceStatusMap.put(userId, faceStatusMask);
			
			// 更新其他状态，包括lastAction等
			int oldValue = userPlayInfoMask.get(userId);
			// Clear the last action first.
			oldValue &= ~LAST_ACTION_MASK;
			userPlayInfoMask.put(userId, oldValue | USER_INFO_ACTION_SHOW_CARD | USER_INFO_SHOWED_CARD);
		}
		return GameResultCode.SUCCESS;
	}


	public GameResultCode compareCard(String userId, String toUserId) {
		
		String winner = null;
		String loser = null;
		
		if ( !userPlayInfoMask.containsKey(userId) ||  !userPlayInfoMask.containsKey(toUserId)) {
			ServerLog.info(sessionId, "<ZjhGameSessuion.compareCard> "+ userId+ "or "+toUserId+" not in this session???!!!");
			return GameResultCode.ERROR_USER_NOT_IN_SESSION;
		} 
		
		int userInfo = userPlayInfoMask.get(userId);
		int toUserInfo = userPlayInfoMask.get(toUserId);
		int combinedUserInfo = userInfo | toUserInfo;
		if ( (combinedUserInfo & USER_INFO_LOSED_GAME) == USER_INFO_LOSED_GAME 
				|| (combinedUserInfo & USER_INFO_FOLDED_CARD) == USER_INFO_FOLDED_CARD) {
			ServerLog.info(sessionId, "<ZjhGameSessuion.compareCard> "+ userId+ "or "+
				toUserId+" has foled card or losed the game, fail to compare card !!!");
			return GameResultCode.ERROR_CANNOT_COMPARE_CARD;			
		}
		
		// 开始比牌！
		
		int userCardType = cardTypeMap.get(userId).ordinal();
		int toUserCardType = cardTypeMap.get(toUserId).ordinal();
			
		// 特殊情况, 玩家牌是2,3 5, 但对手拿的不是豹子牌，那就只能认为是散牌
		if ( userCardType == SPECIAL_VALUE && toUserCardType != THREE_OF_A_KIND_VALUE) {
				userCardType = HIGH_CARD_VALUE; 
		}
		if (toUserCardType == SPECIAL_VALUE && userCardType != THREE_OF_A_KIND_VALUE) {
				toUserCardType = HIGH_CARD_VALUE;
		}
		
		// 一般情况
		if ( userCardType < toUserCardType ) {
				winner = toUserId;
				loser = userId;
		}
		else if ( userCardType > toUserCardType ) {
				winner = userId;
				loser = toUserId;
		}
		else {
			// 牌型一样，比牌面值
			int userRankMask = rankMaskMap.get(userId);
			int toUserRankMask = rankMaskMap.get(toUserId);
			if ( userRankMask < toUserRankMask ) {
				winner = userId;
				loser = toUserId;
			} else if ( userRankMask > toUserRankMask ) {
				winner = toUserId;
				loser = userId;
			} else {
				// 双方牌值同样大，所以按花色比大小，由于deal()中已经把List<PBPoker>按牌面值从大到小排列，
				// 所以只要按序号取出来，然后一一比较花色即可。
				List<PBPoker> userPBPokers = userPokersMap.get(userId);
				List<PBPoker> toUserPBPokers = userPokersMap.get(toUserId);
				for ( int i = 0; i < ZjhGameConstant.PER_USER_CARD_NUM; i++ ) {
					int userSuitVal = userPBPokers.get(i).getSuit().getNumber();
					int toUserSuitVal = toUserPBPokers.get(i).getSuit().getNumber();
					if (userSuitVal < toUserSuitVal) {
						winner = toUserId;
						loser = userId;
						break;
					} else if (userSuitVal > toUserSuitVal) {
						winner = userId;
						loser = toUserId;
						break;
					} else 
						continue;
				} 
			}
		}
		
		// 比牌结束！
		// 主动挑战比牌的玩家如果输了，要被扣金币。挑战者是ID为userId的玩家。
		if ( loser.equals(userId) ) {
				UserManager.deductAccount(getDBInstance(), userId, -ZjhGameConstant.COMPARE_LOSS, DBConstants.C_CHARGE_SOURCE_ZJH_COMPARE_LOSE);
				ServerLog.info(sessionId, userId + "compares cards loses, get "+ COMPARE_LOSS + " coins loss-.-");
		}
				
		// 刷新winner的状态
		int winnerOldInfo = userPlayInfoMask.get(winner);
		// Clear the last action first.
		winnerOldInfo &= ~LAST_ACTION_MASK;
		userPlayInfoMask.put(winner, winnerOldInfo | USER_INFO_ACTION_COMPARE_CARD );
				
		// 刷新loser的状态
		int loserOldInfo = userPlayInfoMask.get(loser);
		// Clear the last action first.
		loserOldInfo &= ~LAST_ACTION_MASK;
		userPlayInfoMask.put(loser, loserOldInfo | USER_INFO_ACTION_COMPARE_CARD | USER_INFO_LOSED_GAME );
				
		// 有一个玩家输了
		alivePlayerCount.decrementAndGet(); //只要副作用，返回值不要
		GameUserManager.getInstance().findUserById(loser).setLoseGame(true); //设玩家游戏状态为loseGame为true
		
		// 构造userResult以返回
		userResults.clear();
		PBUserResult winnerResult = PBUserResult.newBuilder()
				.setUserId(winner)
				.setWin(true)
				.setGainCoins(0)
				.build();
		userResults.put(winner, winnerResult);
		
		PBUserResult loserResult = PBUserResult.newBuilder()
				.setUserId(loser)
				.setWin(false)
				.setGainCoins(0)
				.build();
		userResults.put(loser, loserResult);
		
		return GameResultCode.SUCCESS;
	}


	public List<PBZJHUserPlayInfo> getUserPlayInfo() {
		
		ServerLog.info(sessionId, "<getUserPlayInfo> someone joins in during game!");
		
		List<PBZJHUserPlayInfo> result =  new ArrayList<PBZJHUserPlayInfo>();
		List<GameUser> gameUsers = getUserList().getAllUsers();
		
		for(GameUser user : gameUsers) {
			result.add( updateUserPlayInfo(user.getUserId()) );
			ServerLog.info(sessionId, "<getUserPlayInfo> add userPlayInfo for !"+ user.getNickName());
		}
		
		return result;
	}

	
	// 一开局， 以及游戏中途玩家加入时，调用这个方法更新userPlayInfoList,以传给客户端
	public PBZJHUserPlayInfo updateUserPlayInfo(String userId) {
		
		List<PBPoker> pbPokers = new ArrayList<PBPoker>();
			
		List<PBPoker> pbPokerList =  userPokersMap.get(userId);
		int faceStatus = faceStatusMap.get(userId);
		for ( PBPoker pbPoker : pbPokerList ) {
			PBPokerRank rank = pbPoker.getRank();
			PBPokerSuit suit = pbPoker.getSuit();
			int pokerId = toPokerId(rank, suit);
			// faceStatus是亮牌掩码， 检查对应位是否为1, 是表示亮牌，否则表示没亮牌
			boolean faceUp = IntegerUtil.testBit(faceStatus, rank.ordinal(), 1);
				
			PBPoker newPBPoker = PBPoker.newBuilder()
							.setPokerId(pokerId)
							.setRank(rank)
							.setSuit(suit)
							.setFaceUp(faceUp)
							.build();
			pbPokers.add(newPBPoker);
		}
			
		PBZJHPoker zjhPoker = PBZJHPoker.newBuilder()
						.addAllPokers(pbPokers)
						.setCardType(cardTypeMap.get(userId))
						.build();
			
		int userPlayInfo = userPlayInfoMask.get(userId);
		int totalBet = totalBetMap.get(userId);
		PBZJHUserAction lastAction = lastAction(userPlayInfo);
		boolean isAutoBet = 
			((userPlayInfo & USER_INFO_AUTO_BET) == USER_INFO_AUTO_BET? true:false);
		boolean hasCheckedCard = 
			((userPlayInfo & USER_INFO_CHECKED_CARD) == USER_INFO_CHECKED_CARD? true:false);
		boolean hasFoldedCard = 
			((userPlayInfo & USER_INFO_FOLDED_CARD) == USER_INFO_FOLDED_CARD? true:false);
		boolean hasShowedCard =
			((userPlayInfo & USER_INFO_SHOWED_CARD) == USER_INFO_SHOWED_CARD? true:false);
		boolean hasLosedGame = 
			((userPlayInfo & USER_INFO_LOSED_GAME) == USER_INFO_LOSED_GAME? true:false);
			
		PBZJHUserPlayInfo pbZjhUserPlayInfo = PBZJHUserPlayInfo.newBuilder()
				.setUserId(userId)
				.setPokers(zjhPoker)
				.setTotalBet(totalBet)
				.setIsAutoBet(isAutoBet)
				.setLastAction(lastAction)
				.setAlreadCheckCard(hasCheckedCard)
				.setAlreadFoldCard(hasFoldedCard)
				.setAlreadShowCard(hasShowedCard)
			   .setAlreadLose(hasLosedGame)
				.build();
		
		return pbZjhUserPlayInfo;
	}

	
	private PBZJHUserAction lastAction(int userPlayInfoMask) {
		
		int lastAction = userPlayInfoMask & LAST_ACTION_MASK;
		switch (lastAction) {
			case USER_INFO_ACTION_BET:
				return PBZJHUserAction.BET;
			case USER_INFO_ACTION_RAISE_BET:
				return PBZJHUserAction.RAISE_BET;
			case USER_INFO_ACTION_AUTO_BET:
				return PBZJHUserAction.AUTO_BET;
			case USER_INFO_CHECKED_CARD:
				return PBZJHUserAction.CHECK_CARD;
			case USER_INFO_ACTION_FOLD_CARD:
				return PBZJHUserAction.FOLD_CARD;
			case USER_INFO_ACTION_COMPARE_CARD:
				return PBZJHUserAction.COMPARE_CARD;
			case USER_INFO_ACTION_SHOW_CARD:
				return PBZJHUserAction.SHOW_CARD;
			case USER_INFO_ACTION_CHANGE_CARD:
				return PBZJHUserAction.CHANGE_CARD;
			default:
				return PBZJHUserAction.NONE;
		}
	}
	
	
	public void setAlivePlayerCount(int playUserCount) {
		alivePlayerCount.set(playUserCount);
	}


	
	public void setTotalBet(int playUserCount) {
		totalBet = singleBet*playUserCount;
	}

	
	public void judgeWhowins() {
		
		for (Map.Entry<String, Integer> entry: userPlayInfoMask.entrySet()) {
			if ( ( entry.getValue() & USER_INFO_LOSED_GAME) == USER_INFO_LOSED_GAME ) 
				continue;
			
			String userId = entry.getKey();
			boolean win = true;
			int gainCoins = totalBet;
				
			PBUserResult result = PBUserResult.newBuilder()
						.setUserId(userId)
						.setWin(win)
						.setGainCoins(gainCoins)
						.build();

			// 必须清除userResults先，因为之前比牌操作的结果也存放在其中
			userResults.clear();
			userResults.put(userId, result);
		}
	}


	// 当玩家中途退出时（指未完成游戏），把其游戏状态设为loseGame
	public void updateQuitPlayerInfo(String userId) {
		
		ServerLog.info(sessionId, "<ZjhGameSession.updateQuitPlayerInfo>" +
				"quits the game, so set he/she to lose game status");
		int playInfoMask = userPlayInfoMask.get(userId);
		userPlayInfoMask.put(userId, playInfoMask | USER_INFO_LOSED_GAME ); 
		// 同时需要把alivePlayerCount递减
		alivePlayerCount.decrementAndGet();
	}
	
	
}