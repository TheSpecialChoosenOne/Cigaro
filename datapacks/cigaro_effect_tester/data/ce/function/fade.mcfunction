$posteffect remove @s cigaro:$(effect)
$posteffect add @s cigaro:$(effect)_out
$scoreboard players operation @s $(timer) = #out ce_const
$scoreboard players set @s $(phase) 2
