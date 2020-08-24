package com.leflat.jass.server;

import com.leflat.jass.common.Card;
import com.leflat.jass.common.Team;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;


public class AsyncTests {
    @Test
    void async_error_test() throws PlayerLeftExpection {
        Card.atout = Card.COLOR_SPADE;
        RemotePlayer player1 = mock(RemotePlayer.class);
        RemotePlayer player2 = mock(RemotePlayer.class);
        RemotePlayer player3 = mock(RemotePlayer.class);
        RemotePlayer player4 = mock(RemotePlayer.class);
        when(player1.getId()).thenReturn(0);
        when(player2.getId()).thenReturn(1);
        when(player3.getId()).thenReturn(2);
        when(player4.getId()).thenReturn(3);
        when(player1.getTeam()).thenReturn(new Team(0));
        when(player2.getTeam()).thenReturn(new Team(0));
        when(player3.getTeam()).thenReturn(new Team(1));
        when(player4.getTeam()).thenReturn(new Team(1));
        doThrow(PlayerLeftExpection.class).when(player3).setHandScore(anyInt(), anyInt(), any());

        var game = new GameController(0);
        game.setNoWait(true);
        game.addPlayer(player1);
        game.addPlayer(player2);
        game.addPlayer(player3);
        game.addPlayer(player4);

        assertThrows(PlayerLeftExpection.class, () -> {
                    game.setHandScoreAsync(new int[]{10, 20}, null);
                });
    }

    @Test
    void async_test() throws PlayerLeftExpection {
        Card.atout = Card.COLOR_SPADE;
        RemotePlayer player1 = mock(RemotePlayer.class);
        RemotePlayer player2 = mock(RemotePlayer.class);
        RemotePlayer player3 = mock(RemotePlayer.class);
        RemotePlayer player4 = mock(RemotePlayer.class);
        when(player1.getId()).thenReturn(0);
        when(player2.getId()).thenReturn(1);
        when(player3.getId()).thenReturn(2);
        when(player4.getId()).thenReturn(3);
        var t1 = new Team(0);
        var t2 = new Team(1);
        t1.addScore(100);
        t2.addScore(200);
        when(player1.getTeam()).thenReturn(t1);
        when(player2.getTeam()).thenReturn(t1);
        when(player3.getTeam()).thenReturn(t2);
        when(player4.getTeam()).thenReturn(t2);

        var game = new GameController(0);
        game.setNoWait(true);
        game.addPlayer(player1);
        game.addPlayer(player2);
        game.addPlayer(player3);
        game.addPlayer(player4);

        game.setTeamsScoreAsync();

        verify(player1).setScores(100, 0);
        verify(player2).setScores(100, 0);
        verify(player3).setScores(200, 0);
        verify(player4).setScores(200, 0);
    }
}
