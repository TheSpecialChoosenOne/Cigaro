$execute as @a[scores={$(phase)=1,$(timer)=1..}] run scoreboard players remove @s $(timer) 1
$execute as @a[scores={$(phase)=1,$(timer)=0}] run function ce:fade {phase:"$(phase)",timer:"$(timer)",effect:"$(effect)"}
$execute as @a[scores={$(phase)=2,$(timer)=1..}] run scoreboard players remove @s $(timer) 1
$execute as @a[scores={$(phase)=2,$(timer)=0}] run function ce:finish {phase:"$(phase)",timer:"$(timer)",effect:"$(effect)"}
