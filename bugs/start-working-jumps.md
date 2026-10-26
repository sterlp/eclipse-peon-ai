# New Issues:
1. Started time jumps when I used once cancel or maybe compact of the sub agent. Then the compact runs the time jumps of the slave agent now. Seems like a old time in the compact code?

# Not confirmed
Compact seems also to be to early -- auto compact at turn start should trigger than the current agent memory is too full. It seems that we estimate it in total. We should take the correct count from the memory and only estimate the new message we would add and see if this is too much > threshold. (only a feeling