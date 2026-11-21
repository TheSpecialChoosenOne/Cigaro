$data modify storage ce:args action set value "$(a)"
$data modify storage ce:args value set value "$(b)"
execute if data storage ce:args {action:"remove"} run function ce:remove_dispatch
$execute if data storage ce:args {action:"puff"} unless data storage ce:args {value:""} run function ce:start_puff_args {seconds:"$(b)"}
$execute if data storage ce:args {action:"mint_chill"} unless data storage ce:args {value:""} run function ce:start_mint_chill_args {seconds:"$(b)"}
$execute if data storage ce:args {action:"spice_heat"} unless data storage ce:args {value:""} run function ce:start_spice_heat_args {seconds:"$(b)"}
$execute if data storage ce:args {action:"shadow_noir"} unless data storage ce:args {value:""} run function ce:start_shadow_noir_args {seconds:"$(b)"}
$execute if data storage ce:args {action:"withdrawal"} unless data storage ce:args {value:""} run function ce:start_withdrawal_args {seconds:"$(b)"}
$execute if data storage ce:args {action:"killer_trip"} unless data storage ce:args {value:""} run function ce:start_killer_trip_args {seconds:"$(b)"}
