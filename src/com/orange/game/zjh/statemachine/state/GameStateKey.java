package com.orange.game.zjh.statemachine.state;

public enum GameStateKey {
	CREATE (0),
	WAITING (1),
	PLAYING (2),
	SUSPEND (3),
	FINISH (4), 

	CHECK_USER_COUNT(11), ONE_USER_WAITING(12), WAIT_FOR_START_GAME(13), 
	PLAY_USER_QUIT(14),  DEAL(15), SELECT_NEXT_PLAYER(16), 
	WAIT_NEXT_PLAYER_PLAY(17), TAKEN_OVER_USER_WAIT(18),PLAYER_BET(19),
	PLAYER_CHECK_CARD(20), PLAYER_FOLD_CARD(21), PLAYER_SHOW_CARD(22),
	PLAYER_COMPARE_CARD(23),  COMPLETE_GAME(24), SHOW_RESULT(25), 
	
	;
	
	
	final int value;
	
	GameStateKey(int value){
		this.value = value;
	}
}
