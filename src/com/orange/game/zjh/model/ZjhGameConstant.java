
package com.orange.game.zjh.model;


import com.orange.game.constants.DBConstants;


public class ZjhGameConstant {
	
	public static final String ZJH_GAME_ID = DBConstants.GAME_ID_ZJH;
	
	public static final int PER_USER_CARD_NUM = 3; // each user has 3 cards;
	public static final int ALL_CARD_NUM = 52;
	public static final int PER_SUIT_NUM = 13; // 2-10 ,J, Q ,K
	public static final int SUIT_TYPE_NUM = 4; // spade, heart, club, diamond
	
	// for card type
	public static final int RANK_MASK = 1 << (PER_SUIT_NUM) - 1; // binary: 11111111111, from left to right, each bit
																					 // represents A, K, Q, J, 10, 9, ..., 2
	public static final int SUIT_MASK = 1 << (SUIT_TYPE_NUM) - 1; // binary: 1111,from left to right, each bit represents spade, club, heart, diamond
	
	public static final int TYPE_SPECIAL = 0x1FF4;  // 1 1111 1111 0100
	
}
