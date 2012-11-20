package com.orange.game.zjh.model;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.math.RandomUtils;

import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBPoker;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBPokerRank;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBPokerSuit;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHCardType;

public class ZjhGameTestData {

	private ZjhGameTestData() {
	}
	
	final static ZjhGameTestData test = new ZjhGameTestData();
	
	public static ZjhGameTestData getInstance() {
		return test;
	}
	
	List<PBPoker> dispatchPokersForTest(PBZJHCardType cardType) {
		switch (cardType.getNumber()) {
			case PBZJHCardType.SPECIAL_VALUE:
				return specialPokers();
			case PBZJHCardType.THREE_OF_A_KIND_VALUE:
				return threeOfAKindPokers();
			case PBZJHCardType.STRAIGHT_FLUSH_VALUE:
				return straightFlushPokers();
			case PBZJHCardType.FLUSH_VALUE:
				return straightFlushPokers();
			case PBZJHCardType.STRAIGHT_VALUE:
				return straightPokers();
			case PBZJHCardType.PAIR_VALUE:
				return pairPokers();
			default:
				return null;
		}
		
	}

	
	private List<PBPoker> straightPokers() {
		// TODO Auto-generated method stub
		return null;
	}

	private List<PBPoker> straightFlushPokers() {
		// TODO Auto-generated method stub
		return null;
	}

	private List<PBPoker> threeOfAKindPokers() {
		// TODO Auto-generated method stub
		return null;
	}

	private List<PBPoker> pairPokers() {
		// TODO Auto-generated method stub
		return null;
	}

	private List<PBPoker> specialPokers() {
		
		PBPokerRank[] specialRank = { PBPokerRank.POKER_RANK_2,
				PBPokerRank.POKER_RANK_3, PBPokerRank.POKER_RANK_5 };
		
		List<PBPoker> result = new ArrayList<PBPoker>();
		PBPokerRank rank = null;
		PBPokerSuit suit = null;
		PBPoker pbPoker = null;
		int pokerId;
		boolean faceUp = false;
		
		for (int i = 0; i <ZjhGameConstant.PER_USER_CARD_NUM; i++) {
			rank = specialRank[i];
			suit = PBPokerSuit.values()[RandomUtils.nextInt(4)];
			pokerId = toPokerId(rank, suit); 		
			
			pbPoker = PBPoker.newBuilder()
					.setPokerId(pokerId)
					.setRank(rank)
					.setSuit(suit)
					.setFaceUp(faceUp)
					.build();
			result.add(pbPoker);		
		}
		return  result;
	}
	
	
	private int toPokerId(PBPokerRank rank, PBPokerSuit suit) {
		
		// id start from 0
		int id = 0;
		int rankVal = rank.getNumber();
		int suitVal = suit.getNumber();
		
		id = (rankVal-2) * ZjhGameConstant.SUIT_TYPE_NUM + (suitVal-1);
		
		return id;
	}
}
