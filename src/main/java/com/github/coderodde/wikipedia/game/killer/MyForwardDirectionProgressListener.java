package com.github.coderodde.wikipedia.game.killer;

import io.github.coderodde.graph.pathfinding.delayed.DirectionProgressListener;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 */
final class MyForwardDirectionProgressListener 
        extends DirectionProgressListener<String> {

    final AtomicInteger count = new AtomicInteger(0);
    private MyBackwardDirectionProgressListener oppositeListener;
    
    public void setOppositeListener(
            MyBackwardDirectionProgressListener oppositeListener) {
        this.oppositeListener = oppositeListener;
    }
    
    @Override
    public void onExpansion(final String node, final long durationMillis) {
        super.onExpansion(node, durationMillis);
        
        count.incrementAndGet();
        
        System.out.printf(
            "Expanded in forward direction:  [%s], %d/%d%n",
            node,
            count.get(),
            count.get() + oppositeListener.count.get());
    }
}
