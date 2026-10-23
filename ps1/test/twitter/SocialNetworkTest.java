/* Copyright (c) 2007-2016 MIT 6.005 course staff, all rights reserved.
 * Redistribution of original or derived work requires permission of course staff.
 */
package twitter;

import static org.junit.Assert.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public class SocialNetworkTest {

    /*
     * Testing strategy
     * 
     * guessFollowsGraph(tweets):
     *   partition on number of tweets: 0, 1, many
     *   partition on mentions per tweet: 0, 1, many
     *   partition on tweets per author: 1, many
     *   extra: case-insensitive usernames, self-mentions, email addresses
     *   The postcondition is underdetermined (other evidence may be used), so
     *   tests only check that required follows are present (contains), never
     *   that the whole graph equals an exact map.
     * 
     * influencers(graph):
     *   partition on graph: empty, one user with no followers, one influencer,
     *     several influencers with different counts, ties
     *   The spec returns ALL distinct usernames (keys and followed users) in
     *   descending follower count. Order among ties is unspecified, so tie tests
     *   check only the groups, not the order inside a tie.
     */

    private static final Instant d1 = Instant.parse("2016-02-17T10:00:00Z");

    private static Set<String> set(String... names) {
        return new HashSet<>(Arrays.asList(names));
    }

    // follows set of a user, case-insensitively; empty set if the user is not a key
    private static Set<String> followsOf(Map<String, Set<String>> graph, String user) {
        Set<String> result = new HashSet<>();
        for (Map.Entry<String, Set<String>> e : graph.entrySet()) {
            if (e.getKey().equalsIgnoreCase(user)) {
                for (String s : e.getValue()) {
                    result.add(s.toLowerCase());
                }
            }
        }
        return result;
    }

    @Test(expected=AssertionError.class)
    public void testAssertionsEnabled() {
        assert false; // make sure assertions are enabled with VM argument: -ea
    }

    // ---------- guessFollowsGraph ----------

    // 1. empty list of tweets -> empty graph
    @Test
    public void testGuessFollowsGraphEmpty() {
        Map<String, Set<String>> followsGraph = SocialNetwork.guessFollowsGraph(new ArrayList<>());

        assertTrue("expected empty graph", followsGraph.isEmpty());
    }

    // 2. tweets without mentions -> nobody follows anybody
    @Test
    public void testGuessFollowsGraphNoMentions() {
        List<Tweet> tweets = Arrays.asList(
                new Tweet(1, "alice", "just a normal tweet", d1),
                new Tweet(2, "bob", "email me at bob@mit.edu", d1));
        Map<String, Set<String>> graph = SocialNetwork.guessFollowsGraph(tweets);

        for (Set<String> follows : graph.values()) {
            assertTrue("nobody should follow anybody", follows.isEmpty());
        }
    }

    // 3. single mention -> author follows the mentioned user
    @Test
    public void testGuessFollowsGraphSingleMention() {
        List<Tweet> tweets = Arrays.asList(new Tweet(1, "ernie", "hi @bert", d1));
        Map<String, Set<String>> graph = SocialNetwork.guessFollowsGraph(tweets);

        assertTrue("ernie should follow bert", followsOf(graph, "ernie").contains("bert"));
    }

    // 4. multiple mentions in one tweet -> author follows all of them
    @Test
    public void testGuessFollowsGraphMultipleMentions() {
        List<Tweet> tweets = Arrays.asList(
                new Tweet(1, "ernie", "hello @bert and @elmo and @grover", d1));
        Map<String, Set<String>> graph = SocialNetwork.guessFollowsGraph(tweets);

        assertTrue(followsOf(graph, "ernie").containsAll(set("bert", "elmo", "grover")));
    }

    // 5. multiple tweets from one user -> mentions from all tweets are captured
    @Test
    public void testGuessFollowsGraphMultipleTweetsOneUser() {
        List<Tweet> tweets = Arrays.asList(
                new Tweet(1, "ernie", "hi @bert", d1),
                new Tweet(2, "ernie", "yo @elmo", d1),
                new Tweet(3, "ernie", "again @bert", d1));
        Map<String, Set<String>> graph = SocialNetwork.guessFollowsGraph(tweets);

        assertTrue(followsOf(graph, "ernie").containsAll(set("bert", "elmo")));
    }

    // extra: usernames are case-insensitive and users cannot follow themselves
    @Test
    public void testGuessFollowsGraphCaseInsensitiveAndNoSelfFollow() {
        List<Tweet> tweets = Arrays.asList(new Tweet(1, "Ernie", "hi @BERT and @ernie", d1));
        Map<String, Set<String>> graph = SocialNetwork.guessFollowsGraph(tweets);

        assertTrue(followsOf(graph, "ernie").contains("bert"));
        assertFalse("a user cannot follow themselves", followsOf(graph, "ernie").contains("ernie"));
    }

    // ---------- influencers ----------

    // 6. empty graph -> empty list
    @Test
    public void testInfluencersEmpty() {
        Map<String, Set<String>> followsGraph = new HashMap<>();
        List<String> influencers = SocialNetwork.influencers(followsGraph);

        assertTrue("expected empty list", influencers.isEmpty());
    }

    // 7. single user without followers -> only that user, nobody has followers
    @Test
    public void testInfluencersSingleUserNoFollowers() {
        Map<String, Set<String>> graph = new HashMap<>();
        graph.put("alice", new HashSet<String>());
        List<String> influencers = SocialNetwork.influencers(graph);

        assertEquals("spec returns every distinct username", Arrays.asList("alice"), influencers);
    }

    // 8. single influencer -> the only followed user comes first
    @Test
    public void testInfluencersSingleInfluencer() {
        Map<String, Set<String>> graph = new HashMap<>();
        graph.put("alice", set("bob"));
        List<String> influencers = SocialNetwork.influencers(graph);

        assertEquals(2, influencers.size());
        assertEquals("bob", influencers.get(0));
        assertEquals("alice", influencers.get(1));
    }

    // 9. multiple influencers -> descending order of follower count (c=3, b=2, others=0)
    @Test
    public void testInfluencersMultipleInfluencers() {
        Map<String, Set<String>> graph = new HashMap<>();
        graph.put("a", set("b", "c"));
        graph.put("d", set("c"));
        graph.put("e", set("b", "c"));
        List<String> influencers = SocialNetwork.influencers(graph);

        assertEquals(5, influencers.size());
        assertEquals("c", influencers.get(0));
        assertEquals("b", influencers.get(1));
        assertEquals(set("a", "d", "e"), new HashSet<>(influencers.subList(2, 5)));
    }

    // 10. tied influence -> tied users are adjacent, above users with fewer followers
    @Test
    public void testInfluencersTied() {
        Map<String, Set<String>> graph = new HashMap<>();
        graph.put("a", set("c"));
        graph.put("b", set("d"));
        List<String> influencers = SocialNetwork.influencers(graph);

        assertEquals(4, influencers.size());
        assertEquals(set("c", "d"), new HashSet<>(influencers.subList(0, 2)));
        assertEquals(set("a", "b"), new HashSet<>(influencers.subList(2, 4)));
    }

    /*
     * Warning: all the tests you write here must be runnable against any
     * SocialNetwork class that follows the spec. It will be run against several
     * staff implementations of SocialNetwork, which will be done by overwriting
     * (temporarily) your version of SocialNetwork with the staff's version.
     * DO NOT strengthen the spec of SocialNetwork or its methods.
     * 
     * In particular, your test cases must not call helper methods of your own
     * that you have put in SocialNetwork, because that means you're testing a
     * stronger spec than SocialNetwork says. If you need such helper methods,
     * define them in a different class. If you only need them in this test
     * class, then keep them in this test class.
     */

}