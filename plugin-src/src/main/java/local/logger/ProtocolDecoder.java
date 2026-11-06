package local.logger;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Offline packet names and selected semantic fields. Unimplemented tails remain explicit. */
public final class ProtocolDecoder {
    private static final Map<String, String> NAMES = new LinkedHashMap<>();
    private static final Map<String, String> LAYOUTS = new LinkedHashMap<>();
    private static final Map<Integer, String> VERSIONS = new LinkedHashMap<>();
    static {
        // https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/1.16.2/protocol.json
        // SHA-256: c25e99d506fa129b10a49ab9c587d33b673096517fb34fb7ad7ddd28e648782b
        VERSIONS.put(754, "1.16.5");
        names(754, "HANDSHAKING", true, "0=set_protocol;fe=legacy_server_list_ping");
        names(754, "HANDSHAKING", false, "");
        names(754, "STATUS", true, "0=ping_start;1=ping");
        layout(754, "STATUS", true, 0, "");
        names(754, "STATUS", false, "0=server_info;1=ping");
        names(754, "LOGIN", true, "0=login_start;1=encryption_begin;2=login_plugin_response");
        layout(754, "LOGIN", true, 0, "username=string");
        names(754, "LOGIN", false, "0=disconnect;1=encryption_begin;2=success;3=compress;4=login_plugin_request");
        layout(754, "LOGIN", false, 2, "uuid=UUID;username=string");
        layout(754, "LOGIN", false, 3, "threshold=varint");
        names(754, "PLAY", true, "0=teleport_confirm;1=query_block_nbt;2=set_difficulty;3=chat;4=client_command;5=settings;6=tab_complete;7=transaction;8=enchant_item;9=window_click;a=close_window;b=custom_payload;c=edit_book;d=query_entity_nbt;e=use_entity;f=generate_structure;10=keep_alive;11=lock_difficulty;12=position;13=position_look;14=look;15=flying;16=vehicle_move;17=steer_boat;18=pick_item;19=craft_recipe_request;1a=abilities;1b=block_dig;1c=entity_action;1d=steer_vehicle;1e=recipe_book;1f=displayed_recipe;20=name_item;21=resource_pack_receive;22=advancement_tab;23=select_trade;24=set_beacon_effect;25=held_item_slot;26=update_command_block;27=update_command_block_minecart;28=set_creative_slot;29=update_jigsaw_block;2a=update_structure_block;2b=update_sign;2c=arm_animation;2d=spectate;2e=block_place;2f=use_item");
        layout(754, "PLAY", true, 0, "teleportId=varint");
        layout(754, "PLAY", true, 3, "message=string");
        layout(754, "PLAY", true, 4, "actionId=varint");
        layout(754, "PLAY", true, 7, "windowId=i8;action=i16;accepted=bool");
        layout(754, "PLAY", true, 10, "windowId=u8");
        layout(754, "PLAY", true, 14, "target=varint;mouse=varint;x=unsupported;y=unsupported;z=unsupported;hand=unsupported;sneaking=bool");
        layout(754, "PLAY", true, 16, "keepAliveId=i64");
        layout(754, "PLAY", true, 18, "x=f64;y=f64;z=f64;onGround=bool");
        layout(754, "PLAY", true, 19, "x=f64;y=f64;z=f64;yaw=f32;pitch=f32;onGround=bool");
        layout(754, "PLAY", true, 20, "yaw=f32;pitch=f32;onGround=bool");
        layout(754, "PLAY", true, 21, "onGround=bool");
        layout(754, "PLAY", true, 22, "x=f64;y=f64;z=f64;yaw=f32;pitch=f32");
        layout(754, "PLAY", true, 23, "leftPaddle=bool;rightPaddle=bool");
        layout(754, "PLAY", true, 26, "flags=i8");
        layout(754, "PLAY", true, 27, "status=varint;location=position;face=i8");
        layout(754, "PLAY", true, 28, "entityId=varint;actionId=varint;jumpBoost=varint");
        layout(754, "PLAY", true, 29, "sideways=f32;forward=f32;jump=u8");
        layout(754, "PLAY", true, 37, "slotId=i16");
        layout(754, "PLAY", true, 44, "hand=varint");
        layout(754, "PLAY", true, 46, "hand=varint;location=position;direction=varint;cursorX=f32;cursorY=f32;cursorZ=f32;insideBlock=bool");
        layout(754, "PLAY", true, 47, "hand=varint");
        names(754, "PLAY", false, "0=spawn_entity;1=spawn_entity_experience_orb;2=spawn_entity_living;3=spawn_entity_painting;4=named_entity_spawn;5=animation;6=statistics;7=acknowledge_player_digging;8=block_break_animation;9=tile_entity_data;a=block_action;b=block_change;c=boss_bar;d=difficulty;e=chat;f=tab_complete;10=declare_commands;11=transaction;12=close_window;13=window_items;14=craft_progress_bar;15=set_slot;16=set_cooldown;17=custom_payload;18=named_sound_effect;19=kick_disconnect;1a=entity_status;1b=explosion;1c=unload_chunk;1d=game_state_change;1e=open_horse_window;1f=keep_alive;20=map_chunk;21=world_event;22=world_particles;23=update_light;24=login;25=map;26=trade_list;27=rel_entity_move;28=entity_move_look;29=entity_look;2a=entity;2b=vehicle_move;2c=open_book;2d=open_window;2e=open_sign_entity;2f=craft_recipe_response;30=abilities;31=combat_event;32=player_info;33=face_player;34=position;35=unlock_recipes;36=entity_destroy;37=remove_entity_effect;38=resource_pack_send;39=respawn;3a=entity_head_rotation;3b=multi_block_change;3c=select_advancement_tab;3d=world_border;3e=camera;3f=held_item_slot;40=update_view_position;41=update_view_distance;42=spawn_position;43=scoreboard_display_objective;44=entity_metadata;45=attach_entity;46=entity_velocity;47=entity_equipment;48=experience;49=update_health;4a=scoreboard_objective;4b=set_passengers;4c=teams;4d=scoreboard_score;4e=update_time;4f=title;50=entity_sound_effect;51=sound_effect;52=stop_sound;53=playerlist_header;54=nbt_query_response;55=collect;56=entity_teleport;57=advancements;58=entity_update_attributes;59=entity_effect;5a=declare_recipes;5b=tags");
        layout(754, "PLAY", false, 14, "message=string;position=i8;sender=UUID");
        layout(754, "PLAY", false, 17, "windowId=i8;action=i16;accepted=bool");
        layout(754, "PLAY", false, 18, "windowId=u8");
        layout(754, "PLAY", false, 26, "entityId=i32;entityStatus=i8");
        layout(754, "PLAY", false, 31, "keepAliveId=i64");
        layout(754, "PLAY", false, 43, "x=f64;y=f64;z=f64;yaw=f32;pitch=f32");
        layout(754, "PLAY", false, 48, "flags=i8;flyingSpeed=f32;walkingSpeed=f32");
        layout(754, "PLAY", false, 52, "x=f64;y=f64;z=f64;yaw=f32;pitch=f32;flags=i8;teleportId=varint");
        layout(754, "PLAY", false, 63, "slot=i8");
        layout(754, "PLAY", false, 70, "entityId=varint;velocity=vec3i16");
        layout(754, "PLAY", false, 73, "health=f32;food=varint;foodSaturation=f32");
        // https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/1.21.4/protocol.json
        // SHA-256: 94a9b1557ca432cfa175b4e512ba7b18bda68cd9ae8fa123a98e4ba02e636b93
        VERSIONS.put(769, "1.21.4");
        names(769, "HANDSHAKING", true, "0=set_protocol;fe=legacy_server_list_ping");
        names(769, "HANDSHAKING", false, "");
        names(769, "STATUS", true, "0=ping_start;1=ping");
        layout(769, "STATUS", true, 0, "");
        names(769, "STATUS", false, "0=server_info;1=ping");
        names(769, "LOGIN", true, "0=login_start;1=encryption_begin;2=login_plugin_response;3=login_acknowledged;4=cookie_response");
        layout(769, "LOGIN", true, 0, "username=string;playerUUID=UUID");
        layout(769, "LOGIN", true, 3, "");
        names(769, "LOGIN", false, "0=disconnect;1=encryption_begin;2=success;3=compress;4=login_plugin_request;5=cookie_request");
        layout(769, "LOGIN", false, 2, "uuid=UUID;username=string;properties=unsupported");
        layout(769, "LOGIN", false, 3, "threshold=varint");
        names(769, "CONFIGURATION", true, "0=settings;1=cookie_response;2=custom_payload;3=finish_configuration;4=keep_alive;5=pong;6=resource_pack_receive;7=select_known_packs;8=custom_report_details;9=server_links");
        layout(769, "CONFIGURATION", true, 3, "");
        layout(769, "CONFIGURATION", true, 4, "keepAliveId=i64");
        names(769, "CONFIGURATION", false, "0=cookie_request;1=custom_payload;2=disconnect;3=finish_configuration;4=keep_alive;5=ping;6=reset_chat;7=registry_data;8=remove_resource_pack;9=add_resource_pack;a=store_cookie;b=transfer;c=feature_flags;d=tags;e=select_known_packs;f=custom_report_details;10=server_links");
        layout(769, "CONFIGURATION", false, 3, "");
        layout(769, "CONFIGURATION", false, 4, "keepAliveId=i64");
        layout(769, "CONFIGURATION", false, 6, "");
        names(769, "PLAY", true, "0=teleport_confirm;1=query_block_nbt;2=select_bundle_item;3=set_difficulty;4=message_acknowledgement;5=chat_command;6=chat_command_signed;7=chat_message;8=chat_session_update;9=chunk_batch_received;a=client_command;b=tick_end;c=settings;d=tab_complete;e=configuration_acknowledged;f=enchant_item;10=window_click;11=close_window;12=set_slot_state;13=cookie_response;14=custom_payload;15=debug_sample_subscription;16=edit_book;17=query_entity_nbt;18=use_entity;19=generate_structure;1a=keep_alive;1b=lock_difficulty;1c=position;1d=position_look;1e=look;1f=flying;20=vehicle_move;21=steer_boat;22=pick_item_from_block;23=pick_item_from_entity;24=ping_request;25=craft_recipe_request;26=abilities;27=block_dig;28=entity_action;29=player_input;2a=player_loaded;2b=pong;2c=recipe_book;2d=displayed_recipe;2e=name_item;2f=resource_pack_receive;30=advancement_tab;31=select_trade;32=set_beacon_effect;33=held_item_slot;34=update_command_block;35=update_command_block_minecart;36=set_creative_slot;37=update_jigsaw_block;38=update_structure_block;39=update_sign;3a=arm_animation;3b=spectate;3c=block_place;3d=use_item");
        layout(769, "PLAY", true, 0, "teleportId=varint");
        layout(769, "PLAY", true, 5, "command=string");
        layout(769, "PLAY", true, 6, "command=string;timestamp=i64;salt=i64;argumentSignatures=unsupported;messageCount=varint;acknowledged=unsupported");
        layout(769, "PLAY", true, 7, "message=string;timestamp=i64;salt=i64;signature=unsupported;offset=varint;acknowledged=unsupported");
        layout(769, "PLAY", true, 10, "actionId=varint");
        layout(769, "PLAY", true, 11, "");
        layout(769, "PLAY", true, 14, "");
        layout(769, "PLAY", true, 17, "windowId=varint");
        layout(769, "PLAY", true, 24, "target=varint;mouse=varint;x=unsupported;y=unsupported;z=unsupported;hand=unsupported;sneaking=bool");
        layout(769, "PLAY", true, 26, "keepAliveId=i64");
        layout(769, "PLAY", true, 28, "x=f64;y=f64;z=f64;flags=movement_flags");
        layout(769, "PLAY", true, 29, "x=f64;y=f64;z=f64;yaw=f32;pitch=f32;flags=movement_flags");
        layout(769, "PLAY", true, 30, "yaw=f32;pitch=f32;flags=movement_flags");
        layout(769, "PLAY", true, 31, "flags=movement_flags");
        layout(769, "PLAY", true, 32, "x=f64;y=f64;z=f64;yaw=f32;pitch=f32;onGround=bool");
        layout(769, "PLAY", true, 33, "leftPaddle=bool;rightPaddle=bool");
        layout(769, "PLAY", true, 38, "flags=i8");
        layout(769, "PLAY", true, 39, "status=varint;location=position;face=i8;sequence=varint");
        layout(769, "PLAY", true, 40, "entityId=varint;actionId=varint;jumpBoost=varint");
        layout(769, "PLAY", true, 41, "inputs=inputs");
        layout(769, "PLAY", true, 42, "");
        layout(769, "PLAY", true, 51, "slotId=i16");
        layout(769, "PLAY", true, 58, "hand=varint");
        layout(769, "PLAY", true, 60, "hand=varint;location=position;direction=varint;cursorX=f32;cursorY=f32;cursorZ=f32;insideBlock=bool;worldBorderHit=bool;sequence=varint");
        layout(769, "PLAY", true, 61, "hand=varint;sequence=varint;rotation=vec2f");
        names(769, "PLAY", false, "0=bundle_delimiter;1=spawn_entity;2=spawn_entity_experience_orb;3=animation;4=statistics;5=acknowledge_player_digging;6=block_break_animation;7=tile_entity_data;8=block_action;9=block_change;a=boss_bar;b=difficulty;c=chunk_batch_finished;d=chunk_batch_start;e=chunk_biomes;f=clear_titles;10=tab_complete;11=declare_commands;12=close_window;13=window_items;14=craft_progress_bar;15=set_slot;16=cookie_request;17=set_cooldown;18=chat_suggestions;19=custom_payload;1a=damage_event;1b=debug_sample;1c=hide_message;1d=kick_disconnect;1e=profileless_chat;1f=entity_status;20=sync_entity_position;21=explosion;22=unload_chunk;23=game_state_change;24=open_horse_window;25=hurt_animation;26=initialize_world_border;27=keep_alive;28=map_chunk;29=world_event;2a=world_particles;2b=update_light;2c=login;2d=map;2e=trade_list;2f=rel_entity_move;30=entity_move_look;31=move_minecart;32=entity_look;33=vehicle_move;34=open_book;35=open_window;36=open_sign_entity;37=ping;38=ping_response;39=craft_recipe_response;3a=abilities;3b=player_chat;3c=end_combat_event;3d=enter_combat_event;3e=death_combat_event;3f=player_remove;40=player_info;41=face_player;42=position;43=player_rotation;44=recipe_book_add;45=recipe_book_remove;46=recipe_book_settings;47=entity_destroy;48=remove_entity_effect;49=reset_score;4a=remove_resource_pack;4b=add_resource_pack;4c=respawn;4d=entity_head_rotation;4e=multi_block_change;4f=select_advancement_tab;50=server_data;51=action_bar;52=world_border_center;53=world_border_lerp_size;54=world_border_size;55=world_border_warning_delay;56=world_border_warning_reach;57=camera;58=update_view_position;59=update_view_distance;5a=set_cursor_item;5b=spawn_position;5c=scoreboard_display_objective;5d=entity_metadata;5e=attach_entity;5f=entity_velocity;60=entity_equipment;61=experience;62=update_health;63=held_item_slot;64=scoreboard_objective;65=set_passengers;66=set_player_inventory;67=teams;68=scoreboard_score;69=simulation_distance;6a=set_title_subtitle;6b=update_time;6c=set_title_text;6d=set_title_time;6e=entity_sound_effect;6f=sound_effect;70=start_configuration;71=stop_sound;72=store_cookie;73=system_chat;74=playerlist_header;75=nbt_query_response;76=collect;77=entity_teleport;78=set_ticking_state;79=step_tick;7a=transfer;7b=advancements;7c=entity_update_attributes;7d=entity_effect;7e=declare_recipes;7f=tags;80=set_projectile_power;81=custom_report_details;82=server_links");
        layout(769, "PLAY", false, 13, "");
        layout(769, "PLAY", false, 18, "windowId=varint");
        layout(769, "PLAY", false, 31, "entityId=i32;entityStatus=i8");
        layout(769, "PLAY", false, 39, "keepAliveId=i64");
        layout(769, "PLAY", false, 51, "x=f64;y=f64;z=f64;yaw=f32;pitch=f32");
        layout(769, "PLAY", false, 58, "flags=i8;flyingSpeed=f32;walkingSpeed=f32");
        layout(769, "PLAY", false, 61, "");
        layout(769, "PLAY", false, 66, "teleportId=varint;x=f64;y=f64;z=f64;dx=f64;dy=f64;dz=f64;yaw=f32;pitch=f32;flags=position_flags");
        layout(769, "PLAY", false, 67, "yaw=f32;pitch=f32");
        layout(769, "PLAY", false, 95, "entityId=varint;velocity=vec3i16");
        layout(769, "PLAY", false, 98, "health=f32;food=varint;foodSaturation=f32");
        layout(769, "PLAY", false, 99, "slot=varint");
        layout(769, "PLAY", false, 112, "");
        // https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/1.21.5/protocol.json
        // SHA-256: dbb950b83dac7f844a4d8b836ec682fa1de5e357d795b4488af36888536f8db1
        VERSIONS.put(770, "1.21.5");
        names(770, "HANDSHAKING", true, "0=set_protocol;fe=legacy_server_list_ping");
        names(770, "HANDSHAKING", false, "");
        names(770, "STATUS", true, "0=ping_start;1=ping");
        layout(770, "STATUS", true, 0, "");
        names(770, "STATUS", false, "0=server_info;1=ping");
        names(770, "LOGIN", true, "0=login_start;1=encryption_begin;2=login_plugin_response;3=login_acknowledged;4=cookie_response");
        layout(770, "LOGIN", true, 0, "username=string;playerUUID=UUID");
        layout(770, "LOGIN", true, 3, "");
        names(770, "LOGIN", false, "0=disconnect;1=encryption_begin;2=success;3=compress;4=login_plugin_request;5=cookie_request");
        layout(770, "LOGIN", false, 2, "uuid=UUID;username=string;properties=unsupported");
        layout(770, "LOGIN", false, 3, "threshold=varint");
        names(770, "CONFIGURATION", true, "0=settings;1=cookie_response;2=custom_payload;3=finish_configuration;4=keep_alive;5=pong;6=resource_pack_receive;7=select_known_packs;8=custom_report_details;9=server_links");
        layout(770, "CONFIGURATION", true, 3, "");
        layout(770, "CONFIGURATION", true, 4, "keepAliveId=i64");
        names(770, "CONFIGURATION", false, "0=cookie_request;1=custom_payload;2=disconnect;3=finish_configuration;4=keep_alive;5=ping;6=reset_chat;7=registry_data;8=remove_resource_pack;9=add_resource_pack;a=store_cookie;b=transfer;c=feature_flags;d=tags;e=select_known_packs;f=custom_report_details;10=server_links");
        layout(770, "CONFIGURATION", false, 3, "");
        layout(770, "CONFIGURATION", false, 4, "keepAliveId=i64");
        layout(770, "CONFIGURATION", false, 6, "");
        names(770, "PLAY", true, "0=teleport_confirm;1=query_block_nbt;2=select_bundle_item;3=set_difficulty;4=message_acknowledgement;5=chat_command;6=chat_command_signed;7=chat_message;8=chat_session_update;9=chunk_batch_received;a=client_command;b=tick_end;c=settings;d=tab_complete;e=configuration_acknowledged;f=enchant_item;10=window_click;11=close_window;12=set_slot_state;13=cookie_response;14=custom_payload;15=debug_sample_subscription;16=edit_book;17=query_entity_nbt;18=use_entity;19=generate_structure;1a=keep_alive;1b=lock_difficulty;1c=position;1d=position_look;1e=look;1f=flying;20=vehicle_move;21=steer_boat;22=pick_item_from_block;23=pick_item_from_entity;24=ping_request;25=craft_recipe_request;26=abilities;27=block_dig;28=entity_action;29=player_input;2a=player_loaded;2b=pong;2c=recipe_book;2d=displayed_recipe;2e=name_item;2f=resource_pack_receive;30=advancement_tab;31=select_trade;32=set_beacon_effect;33=held_item_slot;34=update_command_block;35=update_command_block_minecart;36=set_creative_slot;37=update_jigsaw_block;38=update_structure_block;39=set_test_block;3a=update_sign;3b=arm_animation;3c=test_instance_block_action;3d=spectate;3e=block_place;3f=use_item");
        layout(770, "PLAY", true, 0, "teleportId=varint");
        layout(770, "PLAY", true, 5, "command=string");
        layout(770, "PLAY", true, 6, "command=string;timestamp=i64;salt=i64;argumentSignatures=unsupported;messageCount=varint;acknowledged=unsupported;checksum=u8");
        layout(770, "PLAY", true, 7, "message=string;timestamp=i64;salt=i64;signature=unsupported;offset=varint;acknowledged=unsupported;checksum=u8");
        layout(770, "PLAY", true, 10, "actionId=varint");
        layout(770, "PLAY", true, 11, "");
        layout(770, "PLAY", true, 14, "");
        layout(770, "PLAY", true, 17, "windowId=varint");
        layout(770, "PLAY", true, 24, "target=varint;mouse=varint;x=unsupported;y=unsupported;z=unsupported;hand=unsupported;sneaking=bool");
        layout(770, "PLAY", true, 26, "keepAliveId=i64");
        layout(770, "PLAY", true, 28, "x=f64;y=f64;z=f64;flags=movement_flags");
        layout(770, "PLAY", true, 29, "x=f64;y=f64;z=f64;yaw=f32;pitch=f32;flags=movement_flags");
        layout(770, "PLAY", true, 30, "yaw=f32;pitch=f32;flags=movement_flags");
        layout(770, "PLAY", true, 31, "flags=movement_flags");
        layout(770, "PLAY", true, 32, "x=f64;y=f64;z=f64;yaw=f32;pitch=f32;onGround=bool");
        layout(770, "PLAY", true, 33, "leftPaddle=bool;rightPaddle=bool");
        layout(770, "PLAY", true, 38, "flags=i8");
        layout(770, "PLAY", true, 39, "status=varint;location=position;face=i8;sequence=varint");
        layout(770, "PLAY", true, 40, "entityId=varint;actionId=varint;jumpBoost=varint");
        layout(770, "PLAY", true, 41, "inputs=inputs");
        layout(770, "PLAY", true, 42, "");
        layout(770, "PLAY", true, 51, "slotId=i16");
        layout(770, "PLAY", true, 59, "hand=varint");
        layout(770, "PLAY", true, 62, "hand=varint;location=position;direction=varint;cursorX=f32;cursorY=f32;cursorZ=f32;insideBlock=bool;worldBorderHit=bool;sequence=varint");
        layout(770, "PLAY", true, 63, "hand=varint;sequence=varint;rotation=vec2f");
        names(770, "PLAY", false, "0=bundle_delimiter;1=spawn_entity;2=animation;3=statistics;4=acknowledge_player_digging;5=block_break_animation;6=tile_entity_data;7=block_action;8=block_change;9=boss_bar;a=difficulty;b=chunk_batch_finished;c=chunk_batch_start;d=chunk_biomes;e=clear_titles;f=tab_complete;10=declare_commands;11=close_window;12=window_items;13=craft_progress_bar;14=set_slot;15=cookie_request;16=set_cooldown;17=chat_suggestions;18=custom_payload;19=damage_event;1a=debug_sample;1b=hide_message;1c=kick_disconnect;1d=profileless_chat;1e=entity_status;1f=sync_entity_position;20=explosion;21=unload_chunk;22=game_state_change;23=open_horse_window;24=hurt_animation;25=initialize_world_border;26=keep_alive;27=map_chunk;28=world_event;29=world_particles;2a=update_light;2b=login;2c=map;2d=trade_list;2e=rel_entity_move;2f=entity_move_look;30=move_minecart;31=entity_look;32=vehicle_move;33=open_book;34=open_window;35=open_sign_entity;36=ping;37=ping_response;38=craft_recipe_response;39=abilities;3a=player_chat;3b=end_combat_event;3c=enter_combat_event;3d=death_combat_event;3e=player_remove;3f=player_info;40=face_player;41=position;42=player_rotation;43=recipe_book_add;44=recipe_book_remove;45=recipe_book_settings;46=entity_destroy;47=remove_entity_effect;48=reset_score;49=remove_resource_pack;4a=add_resource_pack;4b=respawn;4c=entity_head_rotation;4d=multi_block_change;4e=select_advancement_tab;4f=server_data;50=action_bar;51=world_border_center;52=world_border_lerp_size;53=world_border_size;54=world_border_warning_delay;55=world_border_warning_reach;56=camera;57=update_view_position;58=update_view_distance;59=set_cursor_item;5a=spawn_position;5b=scoreboard_display_objective;5c=entity_metadata;5d=attach_entity;5e=entity_velocity;5f=entity_equipment;60=experience;61=update_health;62=held_item_slot;63=scoreboard_objective;64=set_passengers;65=set_player_inventory;66=teams;67=scoreboard_score;68=simulation_distance;69=set_title_subtitle;6a=update_time;6b=set_title_text;6c=set_title_time;6d=entity_sound_effect;6e=sound_effect;6f=start_configuration;70=stop_sound;71=store_cookie;72=system_chat;73=playerlist_header;74=nbt_query_response;75=collect;76=entity_teleport;77=test_instance_block_status;78=set_ticking_state;79=step_tick;7a=transfer;7b=advancements;7c=entity_update_attributes;7d=entity_effect;7e=declare_recipes;7f=tags;80=set_projectile_power;81=custom_report_details;82=server_links");
        layout(770, "PLAY", false, 12, "");
        layout(770, "PLAY", false, 17, "windowId=varint");
        layout(770, "PLAY", false, 30, "entityId=i32;entityStatus=i8");
        layout(770, "PLAY", false, 38, "keepAliveId=i64");
        layout(770, "PLAY", false, 50, "x=f64;y=f64;z=f64;yaw=f32;pitch=f32");
        layout(770, "PLAY", false, 57, "flags=i8;flyingSpeed=f32;walkingSpeed=f32");
        layout(770, "PLAY", false, 60, "");
        layout(770, "PLAY", false, 65, "teleportId=varint;x=f64;y=f64;z=f64;dx=f64;dy=f64;dz=f64;yaw=f32;pitch=f32;flags=position_flags");
        layout(770, "PLAY", false, 66, "yaw=f32;pitch=f32");
        layout(770, "PLAY", false, 94, "entityId=varint;velocity=vec3i16");
        layout(770, "PLAY", false, 97, "health=f32;food=varint;foodSaturation=f32");
        layout(770, "PLAY", false, 98, "slot=varint");
        layout(770, "PLAY", false, 111, "");
        // https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/1.21.6/protocol.json
        // SHA-256: 3c1f3071c20e22face100fee775841aff78394dfebb78de5b8f1e01ab32301a2
        VERSIONS.put(771, "1.21.6");
        names(771, "HANDSHAKING", true, "0=set_protocol;fe=legacy_server_list_ping");
        names(771, "HANDSHAKING", false, "");
        names(771, "STATUS", true, "0=ping_start;1=ping");
        layout(771, "STATUS", true, 0, "");
        names(771, "STATUS", false, "0=server_info;1=ping");
        names(771, "LOGIN", true, "0=login_start;1=encryption_begin;2=login_plugin_response;3=login_acknowledged;4=cookie_response");
        layout(771, "LOGIN", true, 0, "username=string;playerUUID=UUID");
        layout(771, "LOGIN", true, 3, "");
        names(771, "LOGIN", false, "0=disconnect;1=encryption_begin;2=success;3=compress;4=login_plugin_request;5=cookie_request");
        layout(771, "LOGIN", false, 2, "uuid=UUID;username=string;properties=unsupported");
        layout(771, "LOGIN", false, 3, "threshold=varint");
        names(771, "CONFIGURATION", true, "0=settings;1=cookie_response;2=custom_payload;3=finish_configuration;4=keep_alive;5=pong;6=resource_pack_receive;7=select_known_packs;8=custom_click_action");
        layout(771, "CONFIGURATION", true, 3, "");
        layout(771, "CONFIGURATION", true, 4, "keepAliveId=i64");
        names(771, "CONFIGURATION", false, "0=cookie_request;1=custom_payload;2=disconnect;3=finish_configuration;4=keep_alive;5=ping;6=reset_chat;7=registry_data;8=remove_resource_pack;9=add_resource_pack;a=store_cookie;b=transfer;c=feature_flags;d=tags;e=select_known_packs;f=custom_report_details;10=server_links;11=clear_dialog;12=show_dialog");
        layout(771, "CONFIGURATION", false, 3, "");
        layout(771, "CONFIGURATION", false, 4, "keepAliveId=i64");
        layout(771, "CONFIGURATION", false, 6, "");
        names(771, "PLAY", true, "0=teleport_confirm;1=query_block_nbt;2=select_bundle_item;3=set_difficulty;4=change_gamemode;5=message_acknowledgement;6=chat_command;7=chat_command_signed;8=chat_message;9=chat_session_update;a=chunk_batch_received;b=client_command;c=tick_end;d=settings;e=tab_complete;f=configuration_acknowledged;10=enchant_item;11=window_click;12=close_window;13=set_slot_state;14=cookie_response;15=custom_payload;16=debug_sample_subscription;17=edit_book;18=query_entity_nbt;19=use_entity;1a=generate_structure;1b=keep_alive;1c=lock_difficulty;1d=position;1e=position_look;1f=look;20=flying;21=vehicle_move;22=steer_boat;23=pick_item_from_block;24=pick_item_from_entity;25=ping_request;26=craft_recipe_request;27=abilities;28=block_dig;29=entity_action;2a=player_input;2b=player_loaded;2c=pong;2d=recipe_book;2e=displayed_recipe;2f=name_item;30=resource_pack_receive;31=advancement_tab;32=select_trade;33=set_beacon_effect;34=held_item_slot;35=update_command_block;36=update_command_block_minecart;37=set_creative_slot;38=update_jigsaw_block;39=update_structure_block;3a=set_test_block;3b=update_sign;3c=arm_animation;3d=spectate;3e=test_instance_block_action;3f=block_place;40=use_item;41=custom_click_action");
        layout(771, "PLAY", true, 0, "teleportId=varint");
        layout(771, "PLAY", true, 6, "command=string");
        layout(771, "PLAY", true, 7, "command=string;timestamp=i64;salt=i64;argumentSignatures=unsupported;messageCount=varint;acknowledged=unsupported;checksum=u8");
        layout(771, "PLAY", true, 8, "message=string;timestamp=i64;salt=i64;signature=unsupported;offset=varint;acknowledged=unsupported;checksum=u8");
        layout(771, "PLAY", true, 11, "actionId=varint");
        layout(771, "PLAY", true, 12, "");
        layout(771, "PLAY", true, 15, "");
        layout(771, "PLAY", true, 18, "windowId=varint");
        layout(771, "PLAY", true, 25, "target=varint;mouse=varint;x=unsupported;y=unsupported;z=unsupported;hand=unsupported;sneaking=bool");
        layout(771, "PLAY", true, 27, "keepAliveId=i64");
        layout(771, "PLAY", true, 29, "x=f64;y=f64;z=f64;flags=movement_flags");
        layout(771, "PLAY", true, 30, "x=f64;y=f64;z=f64;yaw=f32;pitch=f32;flags=movement_flags");
        layout(771, "PLAY", true, 31, "yaw=f32;pitch=f32;flags=movement_flags");
        layout(771, "PLAY", true, 32, "flags=movement_flags");
        layout(771, "PLAY", true, 33, "x=f64;y=f64;z=f64;yaw=f32;pitch=f32;onGround=bool");
        layout(771, "PLAY", true, 34, "leftPaddle=bool;rightPaddle=bool");
        layout(771, "PLAY", true, 39, "flags=i8");
        layout(771, "PLAY", true, 40, "status=varint;location=position;face=i8;sequence=varint");
        layout(771, "PLAY", true, 41, "entityId=varint;actionId=varint;jumpBoost=varint");
        layout(771, "PLAY", true, 42, "inputs=inputs");
        layout(771, "PLAY", true, 43, "");
        layout(771, "PLAY", true, 52, "slotId=i16");
        layout(771, "PLAY", true, 60, "hand=varint");
        layout(771, "PLAY", true, 63, "hand=varint;location=position;direction=varint;cursorX=f32;cursorY=f32;cursorZ=f32;insideBlock=bool;worldBorderHit=bool;sequence=varint");
        layout(771, "PLAY", true, 64, "hand=varint;sequence=varint;rotation=vec2f");
        names(771, "PLAY", false, "0=bundle_delimiter;1=spawn_entity;2=animation;3=statistics;4=acknowledge_player_digging;5=block_break_animation;6=tile_entity_data;7=block_action;8=block_change;9=boss_bar;a=difficulty;b=chunk_batch_finished;c=chunk_batch_start;d=chunk_biomes;e=clear_titles;f=tab_complete;10=declare_commands;11=close_window;12=window_items;13=craft_progress_bar;14=set_slot;15=cookie_request;16=set_cooldown;17=chat_suggestions;18=custom_payload;19=damage_event;1a=debug_sample;1b=hide_message;1c=kick_disconnect;1d=profileless_chat;1e=entity_status;1f=sync_entity_position;20=explosion;21=unload_chunk;22=game_state_change;23=open_horse_window;24=hurt_animation;25=initialize_world_border;26=keep_alive;27=map_chunk;28=world_event;29=world_particles;2a=update_light;2b=login;2c=map;2d=trade_list;2e=rel_entity_move;2f=entity_move_look;30=move_minecart;31=entity_look;32=vehicle_move;33=open_book;34=open_window;35=open_sign_entity;36=ping;37=ping_response;38=craft_recipe_response;39=abilities;3a=player_chat;3b=end_combat_event;3c=enter_combat_event;3d=death_combat_event;3e=player_remove;3f=player_info;40=face_player;41=position;42=player_rotation;43=recipe_book_add;44=recipe_book_remove;45=recipe_book_settings;46=entity_destroy;47=remove_entity_effect;48=reset_score;49=remove_resource_pack;4a=add_resource_pack;4b=respawn;4c=entity_head_rotation;4d=multi_block_change;4e=select_advancement_tab;4f=server_data;50=action_bar;51=world_border_center;52=world_border_lerp_size;53=world_border_size;54=world_border_warning_delay;55=world_border_warning_reach;56=camera;57=update_view_position;58=update_view_distance;59=set_cursor_item;5a=spawn_position;5b=scoreboard_display_objective;5c=entity_metadata;5d=attach_entity;5e=entity_velocity;5f=entity_equipment;60=experience;61=update_health;62=held_item_slot;63=scoreboard_objective;64=set_passengers;65=set_player_inventory;66=teams;67=scoreboard_score;68=simulation_distance;69=set_title_subtitle;6a=update_time;6b=set_title_text;6c=set_title_time;6d=entity_sound_effect;6e=sound_effect;6f=start_configuration;70=stop_sound;71=store_cookie;72=system_chat;73=playerlist_header;74=nbt_query_response;75=collect;76=entity_teleport;77=test_instance_block_status;78=set_ticking_state;79=step_tick;7a=transfer;7b=advancements;7c=entity_update_attributes;7d=entity_effect;7e=declare_recipes;7f=tags;80=set_projectile_power;81=custom_report_details;82=server_links;83=tracked_waypoint;84=clear_dialog;85=show_dialog");
        layout(771, "PLAY", false, 12, "");
        layout(771, "PLAY", false, 17, "windowId=varint");
        layout(771, "PLAY", false, 30, "entityId=i32;entityStatus=i8");
        layout(771, "PLAY", false, 38, "keepAliveId=i64");
        layout(771, "PLAY", false, 50, "x=f64;y=f64;z=f64;yaw=f32;pitch=f32");
        layout(771, "PLAY", false, 57, "flags=i8;flyingSpeed=f32;walkingSpeed=f32");
        layout(771, "PLAY", false, 60, "");
        layout(771, "PLAY", false, 65, "teleportId=varint;x=f64;y=f64;z=f64;dx=f64;dy=f64;dz=f64;yaw=f32;pitch=f32;flags=position_flags");
        layout(771, "PLAY", false, 66, "yaw=f32;pitch=f32");
        layout(771, "PLAY", false, 94, "entityId=varint;velocity=vec3i16");
        layout(771, "PLAY", false, 97, "health=f32;food=varint;foodSaturation=f32");
        layout(771, "PLAY", false, 98, "slot=varint");
        layout(771, "PLAY", false, 111, "");
        // https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/1.21.8/protocol.json
        // SHA-256: 3c1f3071c20e22face100fee775841aff78394dfebb78de5b8f1e01ab32301a2
        VERSIONS.put(772, "1.21.7/1.21.8");
        names(772, "HANDSHAKING", true, "0=set_protocol;fe=legacy_server_list_ping");
        names(772, "HANDSHAKING", false, "");
        names(772, "STATUS", true, "0=ping_start;1=ping");
        layout(772, "STATUS", true, 0, "");
        names(772, "STATUS", false, "0=server_info;1=ping");
        names(772, "LOGIN", true, "0=login_start;1=encryption_begin;2=login_plugin_response;3=login_acknowledged;4=cookie_response");
        layout(772, "LOGIN", true, 0, "username=string;playerUUID=UUID");
        layout(772, "LOGIN", true, 3, "");
        names(772, "LOGIN", false, "0=disconnect;1=encryption_begin;2=success;3=compress;4=login_plugin_request;5=cookie_request");
        layout(772, "LOGIN", false, 2, "uuid=UUID;username=string;properties=unsupported");
        layout(772, "LOGIN", false, 3, "threshold=varint");
        names(772, "CONFIGURATION", true, "0=settings;1=cookie_response;2=custom_payload;3=finish_configuration;4=keep_alive;5=pong;6=resource_pack_receive;7=select_known_packs;8=custom_click_action");
        layout(772, "CONFIGURATION", true, 3, "");
        layout(772, "CONFIGURATION", true, 4, "keepAliveId=i64");
        names(772, "CONFIGURATION", false, "0=cookie_request;1=custom_payload;2=disconnect;3=finish_configuration;4=keep_alive;5=ping;6=reset_chat;7=registry_data;8=remove_resource_pack;9=add_resource_pack;a=store_cookie;b=transfer;c=feature_flags;d=tags;e=select_known_packs;f=custom_report_details;10=server_links;11=clear_dialog;12=show_dialog");
        layout(772, "CONFIGURATION", false, 3, "");
        layout(772, "CONFIGURATION", false, 4, "keepAliveId=i64");
        layout(772, "CONFIGURATION", false, 6, "");
        names(772, "PLAY", true, "0=teleport_confirm;1=query_block_nbt;2=select_bundle_item;3=set_difficulty;4=change_gamemode;5=message_acknowledgement;6=chat_command;7=chat_command_signed;8=chat_message;9=chat_session_update;a=chunk_batch_received;b=client_command;c=tick_end;d=settings;e=tab_complete;f=configuration_acknowledged;10=enchant_item;11=window_click;12=close_window;13=set_slot_state;14=cookie_response;15=custom_payload;16=debug_sample_subscription;17=edit_book;18=query_entity_nbt;19=use_entity;1a=generate_structure;1b=keep_alive;1c=lock_difficulty;1d=position;1e=position_look;1f=look;20=flying;21=vehicle_move;22=steer_boat;23=pick_item_from_block;24=pick_item_from_entity;25=ping_request;26=craft_recipe_request;27=abilities;28=block_dig;29=entity_action;2a=player_input;2b=player_loaded;2c=pong;2d=recipe_book;2e=displayed_recipe;2f=name_item;30=resource_pack_receive;31=advancement_tab;32=select_trade;33=set_beacon_effect;34=held_item_slot;35=update_command_block;36=update_command_block_minecart;37=set_creative_slot;38=update_jigsaw_block;39=update_structure_block;3a=set_test_block;3b=update_sign;3c=arm_animation;3d=spectate;3e=test_instance_block_action;3f=block_place;40=use_item;41=custom_click_action");
        layout(772, "PLAY", true, 0, "teleportId=varint");
        layout(772, "PLAY", true, 6, "command=string");
        layout(772, "PLAY", true, 7, "command=string;timestamp=i64;salt=i64;argumentSignatures=unsupported;messageCount=varint;acknowledged=unsupported;checksum=u8");
        layout(772, "PLAY", true, 8, "message=string;timestamp=i64;salt=i64;signature=unsupported;offset=varint;acknowledged=unsupported;checksum=u8");
        layout(772, "PLAY", true, 11, "actionId=varint");
        layout(772, "PLAY", true, 12, "");
        layout(772, "PLAY", true, 15, "");
        layout(772, "PLAY", true, 18, "windowId=varint");
        layout(772, "PLAY", true, 25, "target=varint;mouse=varint;x=unsupported;y=unsupported;z=unsupported;hand=unsupported;sneaking=bool");
        layout(772, "PLAY", true, 27, "keepAliveId=i64");
        layout(772, "PLAY", true, 29, "x=f64;y=f64;z=f64;flags=movement_flags");
        layout(772, "PLAY", true, 30, "x=f64;y=f64;z=f64;yaw=f32;pitch=f32;flags=movement_flags");
        layout(772, "PLAY", true, 31, "yaw=f32;pitch=f32;flags=movement_flags");
        layout(772, "PLAY", true, 32, "flags=movement_flags");
        layout(772, "PLAY", true, 33, "x=f64;y=f64;z=f64;yaw=f32;pitch=f32;onGround=bool");
        layout(772, "PLAY", true, 34, "leftPaddle=bool;rightPaddle=bool");
        layout(772, "PLAY", true, 39, "flags=i8");
        layout(772, "PLAY", true, 40, "status=varint;location=position;face=i8;sequence=varint");
        layout(772, "PLAY", true, 41, "entityId=varint;actionId=varint;jumpBoost=varint");
        layout(772, "PLAY", true, 42, "inputs=inputs");
        layout(772, "PLAY", true, 43, "");
        layout(772, "PLAY", true, 52, "slotId=i16");
        layout(772, "PLAY", true, 60, "hand=varint");
        layout(772, "PLAY", true, 63, "hand=varint;location=position;direction=varint;cursorX=f32;cursorY=f32;cursorZ=f32;insideBlock=bool;worldBorderHit=bool;sequence=varint");
        layout(772, "PLAY", true, 64, "hand=varint;sequence=varint;rotation=vec2f");
        names(772, "PLAY", false, "0=bundle_delimiter;1=spawn_entity;2=animation;3=statistics;4=acknowledge_player_digging;5=block_break_animation;6=tile_entity_data;7=block_action;8=block_change;9=boss_bar;a=difficulty;b=chunk_batch_finished;c=chunk_batch_start;d=chunk_biomes;e=clear_titles;f=tab_complete;10=declare_commands;11=close_window;12=window_items;13=craft_progress_bar;14=set_slot;15=cookie_request;16=set_cooldown;17=chat_suggestions;18=custom_payload;19=damage_event;1a=debug_sample;1b=hide_message;1c=kick_disconnect;1d=profileless_chat;1e=entity_status;1f=sync_entity_position;20=explosion;21=unload_chunk;22=game_state_change;23=open_horse_window;24=hurt_animation;25=initialize_world_border;26=keep_alive;27=map_chunk;28=world_event;29=world_particles;2a=update_light;2b=login;2c=map;2d=trade_list;2e=rel_entity_move;2f=entity_move_look;30=move_minecart;31=entity_look;32=vehicle_move;33=open_book;34=open_window;35=open_sign_entity;36=ping;37=ping_response;38=craft_recipe_response;39=abilities;3a=player_chat;3b=end_combat_event;3c=enter_combat_event;3d=death_combat_event;3e=player_remove;3f=player_info;40=face_player;41=position;42=player_rotation;43=recipe_book_add;44=recipe_book_remove;45=recipe_book_settings;46=entity_destroy;47=remove_entity_effect;48=reset_score;49=remove_resource_pack;4a=add_resource_pack;4b=respawn;4c=entity_head_rotation;4d=multi_block_change;4e=select_advancement_tab;4f=server_data;50=action_bar;51=world_border_center;52=world_border_lerp_size;53=world_border_size;54=world_border_warning_delay;55=world_border_warning_reach;56=camera;57=update_view_position;58=update_view_distance;59=set_cursor_item;5a=spawn_position;5b=scoreboard_display_objective;5c=entity_metadata;5d=attach_entity;5e=entity_velocity;5f=entity_equipment;60=experience;61=update_health;62=held_item_slot;63=scoreboard_objective;64=set_passengers;65=set_player_inventory;66=teams;67=scoreboard_score;68=simulation_distance;69=set_title_subtitle;6a=update_time;6b=set_title_text;6c=set_title_time;6d=entity_sound_effect;6e=sound_effect;6f=start_configuration;70=stop_sound;71=store_cookie;72=system_chat;73=playerlist_header;74=nbt_query_response;75=collect;76=entity_teleport;77=test_instance_block_status;78=set_ticking_state;79=step_tick;7a=transfer;7b=advancements;7c=entity_update_attributes;7d=entity_effect;7e=declare_recipes;7f=tags;80=set_projectile_power;81=custom_report_details;82=server_links;83=tracked_waypoint;84=clear_dialog;85=show_dialog");
        layout(772, "PLAY", false, 12, "");
        layout(772, "PLAY", false, 17, "windowId=varint");
        layout(772, "PLAY", false, 30, "entityId=i32;entityStatus=i8");
        layout(772, "PLAY", false, 38, "keepAliveId=i64");
        layout(772, "PLAY", false, 50, "x=f64;y=f64;z=f64;yaw=f32;pitch=f32");
        layout(772, "PLAY", false, 57, "flags=i8;flyingSpeed=f32;walkingSpeed=f32");
        layout(772, "PLAY", false, 60, "");
        layout(772, "PLAY", false, 65, "teleportId=varint;x=f64;y=f64;z=f64;dx=f64;dy=f64;dz=f64;yaw=f32;pitch=f32;flags=position_flags");
        layout(772, "PLAY", false, 66, "yaw=f32;pitch=f32");
        layout(772, "PLAY", false, 94, "entityId=varint;velocity=vec3i16");
        layout(772, "PLAY", false, 97, "health=f32;food=varint;foodSaturation=f32");
        layout(772, "PLAY", false, 98, "slot=varint");
        layout(772, "PLAY", false, 111, "");
        // https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/1.21.9/protocol.json
        // SHA-256: ae25e140c226738ebce2dedc3c3c6f86afec3a053d485de293c3cca47e7a5509
        VERSIONS.put(773, "1.21.9/1.21.10");
        names(773, "HANDSHAKING", true, "0=set_protocol;fe=legacy_server_list_ping");
        names(773, "HANDSHAKING", false, "");
        names(773, "STATUS", true, "0=ping_start;1=ping");
        layout(773, "STATUS", true, 0, "");
        names(773, "STATUS", false, "0=server_info;1=ping");
        names(773, "LOGIN", true, "0=login_start;1=encryption_begin;2=login_plugin_response;3=login_acknowledged;4=cookie_response");
        layout(773, "LOGIN", true, 0, "username=string;playerUUID=UUID");
        layout(773, "LOGIN", true, 3, "");
        names(773, "LOGIN", false, "0=disconnect;1=encryption_begin;2=success;3=compress;4=login_plugin_request;5=cookie_request");
        layout(773, "LOGIN", false, 2, "uuid=UUID;username=string;properties=unsupported");
        layout(773, "LOGIN", false, 3, "threshold=varint");
        names(773, "CONFIGURATION", true, "0=settings;1=cookie_response;2=custom_payload;3=finish_configuration;4=keep_alive;5=pong;6=resource_pack_receive;7=select_known_packs;8=custom_click_action;9=accept_code_of_conduct");
        layout(773, "CONFIGURATION", true, 3, "");
        layout(773, "CONFIGURATION", true, 4, "keepAliveId=i64");
        layout(773, "CONFIGURATION", true, 9, "");
        names(773, "CONFIGURATION", false, "0=cookie_request;1=custom_payload;2=disconnect;3=finish_configuration;4=keep_alive;5=ping;6=reset_chat;7=registry_data;8=remove_resource_pack;9=add_resource_pack;a=store_cookie;b=transfer;c=feature_flags;d=tags;e=select_known_packs;f=custom_report_details;10=server_links;11=clear_dialog;12=show_dialog;13=code_of_conduct");
        layout(773, "CONFIGURATION", false, 3, "");
        layout(773, "CONFIGURATION", false, 4, "keepAliveId=i64");
        layout(773, "CONFIGURATION", false, 6, "");
        names(773, "PLAY", true, "0=teleport_confirm;1=query_block_nbt;2=select_bundle_item;3=set_difficulty;4=change_gamemode;5=message_acknowledgement;6=chat_command;7=chat_command_signed;8=chat_message;9=chat_session_update;a=chunk_batch_received;b=client_command;c=tick_end;d=settings;e=tab_complete;f=configuration_acknowledged;10=enchant_item;11=window_click;12=close_window;13=set_slot_state;14=cookie_response;15=custom_payload;16=debug_subscription_request;17=edit_book;18=query_entity_nbt;19=use_entity;1a=generate_structure;1b=keep_alive;1c=lock_difficulty;1d=position;1e=position_look;1f=look;20=flying;21=vehicle_move;22=steer_boat;23=pick_item_from_block;24=pick_item_from_entity;25=ping_request;26=craft_recipe_request;27=abilities;28=block_dig;29=entity_action;2a=player_input;2b=player_loaded;2c=pong;2d=recipe_book;2e=displayed_recipe;2f=name_item;30=resource_pack_receive;31=advancement_tab;32=select_trade;33=set_beacon_effect;34=held_item_slot;35=update_command_block;36=update_command_block_minecart;37=set_creative_slot;38=update_jigsaw_block;39=update_structure_block;3a=set_test_block;3b=update_sign;3c=arm_animation;3d=spectate;3e=test_instance_block_action;3f=block_place;40=use_item;41=custom_click_action");
        layout(773, "PLAY", true, 0, "teleportId=varint");
        layout(773, "PLAY", true, 6, "command=string");
        layout(773, "PLAY", true, 7, "command=string;timestamp=i64;salt=i64;argumentSignatures=unsupported;messageCount=varint;acknowledged=unsupported;checksum=u8");
        layout(773, "PLAY", true, 8, "message=string;timestamp=i64;salt=i64;signature=unsupported;offset=varint;acknowledged=unsupported;checksum=u8");
        layout(773, "PLAY", true, 11, "actionId=varint");
        layout(773, "PLAY", true, 12, "");
        layout(773, "PLAY", true, 15, "");
        layout(773, "PLAY", true, 18, "windowId=varint");
        layout(773, "PLAY", true, 25, "target=varint;mouse=varint;x=unsupported;y=unsupported;z=unsupported;hand=unsupported;sneaking=bool");
        layout(773, "PLAY", true, 27, "keepAliveId=i64");
        layout(773, "PLAY", true, 29, "x=f64;y=f64;z=f64;flags=movement_flags");
        layout(773, "PLAY", true, 30, "x=f64;y=f64;z=f64;yaw=f32;pitch=f32;flags=movement_flags");
        layout(773, "PLAY", true, 31, "yaw=f32;pitch=f32;flags=movement_flags");
        layout(773, "PLAY", true, 32, "flags=movement_flags");
        layout(773, "PLAY", true, 33, "x=f64;y=f64;z=f64;yaw=f32;pitch=f32;onGround=bool");
        layout(773, "PLAY", true, 34, "leftPaddle=bool;rightPaddle=bool");
        layout(773, "PLAY", true, 39, "flags=i8");
        layout(773, "PLAY", true, 40, "status=varint;location=position;face=i8;sequence=varint");
        layout(773, "PLAY", true, 41, "entityId=varint;actionId=varint;jumpBoost=varint");
        layout(773, "PLAY", true, 42, "inputs=inputs");
        layout(773, "PLAY", true, 43, "");
        layout(773, "PLAY", true, 52, "slotId=i16");
        layout(773, "PLAY", true, 60, "hand=varint");
        layout(773, "PLAY", true, 63, "hand=varint;location=position;direction=varint;cursorX=f32;cursorY=f32;cursorZ=f32;insideBlock=bool;worldBorderHit=bool;sequence=varint");
        layout(773, "PLAY", true, 64, "hand=varint;sequence=varint;rotation=vec2f");
        names(773, "PLAY", false, "0=bundle_delimiter;1=spawn_entity;2=animation;3=statistics;4=acknowledge_player_digging;5=block_break_animation;6=tile_entity_data;7=block_action;8=block_change;9=boss_bar;a=difficulty;b=chunk_batch_finished;c=chunk_batch_start;d=chunk_biomes;e=clear_titles;f=tab_complete;10=declare_commands;11=close_window;12=window_items;13=craft_progress_bar;14=set_slot;15=cookie_request;16=set_cooldown;17=chat_suggestions;18=custom_payload;19=damage_event;1a=debug_block_value;1b=debug_chunk_value;1c=debug_entity_value;1d=debug_event;1e=debug_sample;1f=hide_message;20=kick_disconnect;21=profileless_chat;22=entity_status;23=sync_entity_position;24=explosion;25=unload_chunk;26=game_state_change;27=game_test_highlight_pos;28=open_horse_window;29=hurt_animation;2a=initialize_world_border;2b=keep_alive;2c=map_chunk;2d=world_event;2e=world_particles;2f=update_light;30=login;31=map;32=trade_list;33=rel_entity_move;34=entity_move_look;35=move_minecart;36=entity_look;37=vehicle_move;38=open_book;39=open_window;3a=open_sign_entity;3b=ping;3c=ping_response;3d=craft_recipe_response;3e=abilities;3f=player_chat;40=end_combat_event;41=enter_combat_event;42=death_combat_event;43=player_remove;44=player_info;45=face_player;46=position;47=player_rotation;48=recipe_book_add;49=recipe_book_remove;4a=recipe_book_settings;4b=entity_destroy;4c=remove_entity_effect;4d=reset_score;4e=remove_resource_pack;4f=add_resource_pack;50=respawn;51=entity_head_rotation;52=multi_block_change;53=select_advancement_tab;54=server_data;55=action_bar;56=world_border_center;57=world_border_lerp_size;58=world_border_size;59=world_border_warning_delay;5a=world_border_warning_reach;5b=camera;5c=update_view_position;5d=update_view_distance;5e=set_cursor_item;5f=spawn_position;60=scoreboard_display_objective;61=entity_metadata;62=attach_entity;63=entity_velocity;64=entity_equipment;65=experience;66=update_health;67=held_item_slot;68=scoreboard_objective;69=set_passengers;6a=set_player_inventory;6b=teams;6c=scoreboard_score;6d=simulation_distance;6e=set_title_subtitle;6f=update_time;70=set_title_text;71=set_title_time;72=entity_sound_effect;73=sound_effect;74=start_configuration;75=stop_sound;76=store_cookie;77=system_chat;78=playerlist_header;79=nbt_query_response;7a=collect;7b=entity_teleport;7c=test_instance_block_status;7d=set_ticking_state;7e=step_tick;7f=transfer;80=advancements;81=entity_update_attributes;82=entity_effect;83=declare_recipes;84=tags;85=set_projectile_power;86=custom_report_details;87=server_links;88=tracked_waypoint;89=clear_dialog;8a=show_dialog");
        layout(773, "PLAY", false, 12, "");
        layout(773, "PLAY", false, 17, "windowId=varint");
        layout(773, "PLAY", false, 34, "entityId=i32;entityStatus=i8");
        layout(773, "PLAY", false, 43, "keepAliveId=i64");
        layout(773, "PLAY", false, 55, "x=f64;y=f64;z=f64;yaw=f32;pitch=f32");
        layout(773, "PLAY", false, 62, "flags=i8;flyingSpeed=f32;walkingSpeed=f32");
        layout(773, "PLAY", false, 65, "");
        layout(773, "PLAY", false, 70, "teleportId=varint;x=f64;y=f64;z=f64;dx=f64;dy=f64;dz=f64;yaw=f32;pitch=f32;flags=position_flags");
        layout(773, "PLAY", false, 71, "yaw=f32;relativeYaw=bool;pitch=f32;relativePitch=bool");
        layout(773, "PLAY", false, 99, "entityId=varint;velocity=unsupported");
        layout(773, "PLAY", false, 102, "health=f32;food=varint;foodSaturation=f32");
        layout(773, "PLAY", false, 103, "slot=varint");
        layout(773, "PLAY", false, 116, "");
        // https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/1.21.11/protocol.json
        // SHA-256: 070bd9c18a10806eca1aea83ebc2671a644203b216fb2e37b87a663b63b6bf2b
        VERSIONS.put(774, "1.21.11");
        names(774, "HANDSHAKING", true, "0=set_protocol;fe=legacy_server_list_ping");
        names(774, "HANDSHAKING", false, "");
        names(774, "STATUS", true, "0=ping_start;1=ping");
        layout(774, "STATUS", true, 0, "");
        names(774, "STATUS", false, "0=server_info;1=ping");
        names(774, "LOGIN", true, "0=login_start;1=encryption_begin;2=login_plugin_response;3=login_acknowledged;4=cookie_response");
        layout(774, "LOGIN", true, 0, "username=string;playerUUID=UUID");
        layout(774, "LOGIN", true, 3, "");
        names(774, "LOGIN", false, "0=disconnect;1=encryption_begin;2=success;3=compress;4=login_plugin_request;5=cookie_request");
        layout(774, "LOGIN", false, 2, "uuid=UUID;username=string;properties=unsupported");
        layout(774, "LOGIN", false, 3, "threshold=varint");
        names(774, "CONFIGURATION", true, "0=settings;1=cookie_response;2=custom_payload;3=finish_configuration;4=keep_alive;5=pong;6=resource_pack_receive;7=select_known_packs;8=custom_click_action;9=accept_code_of_conduct");
        layout(774, "CONFIGURATION", true, 3, "");
        layout(774, "CONFIGURATION", true, 4, "keepAliveId=i64");
        layout(774, "CONFIGURATION", true, 9, "");
        names(774, "CONFIGURATION", false, "0=cookie_request;1=custom_payload;2=disconnect;3=finish_configuration;4=keep_alive;5=ping;6=reset_chat;7=registry_data;8=remove_resource_pack;9=add_resource_pack;a=store_cookie;b=transfer;c=feature_flags;d=tags;e=select_known_packs;f=custom_report_details;10=server_links;11=clear_dialog;12=show_dialog;13=code_of_conduct");
        layout(774, "CONFIGURATION", false, 3, "");
        layout(774, "CONFIGURATION", false, 4, "keepAliveId=i64");
        layout(774, "CONFIGURATION", false, 6, "");
        names(774, "PLAY", true, "0=teleport_confirm;1=query_block_nbt;2=select_bundle_item;3=set_difficulty;4=change_gamemode;5=message_acknowledgement;6=chat_command;7=chat_command_signed;8=chat_message;9=chat_session_update;a=chunk_batch_received;b=client_command;c=tick_end;d=settings;e=tab_complete;f=configuration_acknowledged;10=enchant_item;11=window_click;12=close_window;13=set_slot_state;14=cookie_response;15=custom_payload;16=debug_subscription_request;17=edit_book;18=query_entity_nbt;19=use_entity;1a=generate_structure;1b=keep_alive;1c=lock_difficulty;1d=position;1e=position_look;1f=look;20=flying;21=vehicle_move;22=steer_boat;23=pick_item_from_block;24=pick_item_from_entity;25=ping_request;26=craft_recipe_request;27=abilities;28=block_dig;29=entity_action;2a=player_input;2b=player_loaded;2c=pong;2d=recipe_book;2e=displayed_recipe;2f=name_item;30=resource_pack_receive;31=advancement_tab;32=select_trade;33=set_beacon_effect;34=held_item_slot;35=update_command_block;36=update_command_block_minecart;37=set_creative_slot;38=update_jigsaw_block;39=update_structure_block;3a=set_test_block;3b=update_sign;3c=arm_animation;3d=spectate;3e=test_instance_block_action;3f=block_place;40=use_item;41=custom_click_action");
        layout(774, "PLAY", true, 0, "teleportId=varint");
        layout(774, "PLAY", true, 6, "command=string");
        layout(774, "PLAY", true, 7, "command=string;timestamp=i64;salt=i64;argumentSignatures=unsupported;messageCount=varint;acknowledged=unsupported;checksum=i8");
        layout(774, "PLAY", true, 8, "message=string;timestamp=i64;salt=i64;signature=unsupported;offset=varint;acknowledged=unsupported;checksum=u8");
        layout(774, "PLAY", true, 11, "actionId=varint");
        layout(774, "PLAY", true, 12, "");
        layout(774, "PLAY", true, 15, "");
        layout(774, "PLAY", true, 18, "windowId=varint");
        layout(774, "PLAY", true, 25, "target=varint;mouse=varint;x=unsupported;y=unsupported;z=unsupported;hand=unsupported;sneaking=bool");
        layout(774, "PLAY", true, 27, "keepAliveId=i64");
        layout(774, "PLAY", true, 29, "x=f64;y=f64;z=f64;flags=movement_flags");
        layout(774, "PLAY", true, 30, "x=f64;y=f64;z=f64;yaw=f32;pitch=f32;flags=movement_flags");
        layout(774, "PLAY", true, 31, "yaw=f32;pitch=f32;flags=movement_flags");
        layout(774, "PLAY", true, 32, "flags=movement_flags");
        layout(774, "PLAY", true, 33, "x=f64;y=f64;z=f64;yaw=f32;pitch=f32;onGround=bool");
        layout(774, "PLAY", true, 34, "leftPaddle=bool;rightPaddle=bool");
        layout(774, "PLAY", true, 39, "flags=i8");
        layout(774, "PLAY", true, 40, "status=varint;location=position;face=i8;sequence=varint");
        layout(774, "PLAY", true, 41, "entityId=varint;actionId=varint;jumpBoost=varint");
        layout(774, "PLAY", true, 42, "inputs=inputs");
        layout(774, "PLAY", true, 43, "");
        layout(774, "PLAY", true, 52, "slotId=i16");
        layout(774, "PLAY", true, 60, "hand=varint");
        layout(774, "PLAY", true, 63, "hand=varint;location=position;direction=varint;cursorX=f32;cursorY=f32;cursorZ=f32;insideBlock=bool;worldBorderHit=bool;sequence=varint");
        layout(774, "PLAY", true, 64, "hand=varint;sequence=varint;rotation=vec2f");
        names(774, "PLAY", false, "0=bundle_delimiter;1=spawn_entity;2=animation;3=statistics;4=acknowledge_player_digging;5=block_break_animation;6=tile_entity_data;7=block_action;8=block_change;9=boss_bar;a=difficulty;b=chunk_batch_finished;c=chunk_batch_start;d=chunk_biomes;e=clear_titles;f=tab_complete;10=declare_commands;11=close_window;12=window_items;13=craft_progress_bar;14=set_slot;15=cookie_request;16=set_cooldown;17=chat_suggestions;18=custom_payload;19=damage_event;1a=debug_block_value;1b=debug_chunk_value;1c=debug_entity_value;1d=debug_event;1e=debug_sample;1f=hide_message;20=kick_disconnect;21=profileless_chat;22=entity_status;23=sync_entity_position;24=explosion;25=unload_chunk;26=game_state_change;27=game_test_highlight_pos;28=open_horse_window;29=hurt_animation;2a=initialize_world_border;2b=keep_alive;2c=map_chunk;2d=world_event;2e=world_particles;2f=update_light;30=login;31=map;32=trade_list;33=rel_entity_move;34=entity_move_look;35=move_minecart;36=entity_look;37=vehicle_move;38=open_book;39=open_window;3a=open_sign_entity;3b=ping;3c=ping_response;3d=craft_recipe_response;3e=abilities;3f=player_chat;40=end_combat_event;41=enter_combat_event;42=death_combat_event;43=player_remove;44=player_info;45=face_player;46=position;47=player_rotation;48=recipe_book_add;49=recipe_book_remove;4a=recipe_book_settings;4b=entity_destroy;4c=remove_entity_effect;4d=reset_score;4e=remove_resource_pack;4f=add_resource_pack;50=respawn;51=entity_head_rotation;52=multi_block_change;53=select_advancement_tab;54=server_data;55=action_bar;56=world_border_center;57=world_border_lerp_size;58=world_border_size;59=world_border_warning_delay;5a=world_border_warning_reach;5b=camera;5c=update_view_position;5d=update_view_distance;5e=set_cursor_item;5f=spawn_position;60=scoreboard_display_objective;61=entity_metadata;62=attach_entity;63=entity_velocity;64=entity_equipment;65=experience;66=update_health;67=held_item_slot;68=scoreboard_objective;69=set_passengers;6a=set_player_inventory;6b=teams;6c=scoreboard_score;6d=simulation_distance;6e=set_title_subtitle;6f=update_time;70=set_title_text;71=set_title_time;72=entity_sound_effect;73=sound_effect;74=start_configuration;75=stop_sound;76=store_cookie;77=system_chat;78=playerlist_header;79=nbt_query_response;7a=collect;7b=entity_teleport;7c=test_instance_block_status;7d=set_ticking_state;7e=step_tick;7f=transfer;80=advancements;81=entity_update_attributes;82=entity_effect;83=declare_recipes;84=tags;85=set_projectile_power;86=custom_report_details;87=server_links;88=tracked_waypoint;89=clear_dialog;8a=show_dialog");
        layout(774, "PLAY", false, 12, "");
        layout(774, "PLAY", false, 17, "windowId=varint");
        layout(774, "PLAY", false, 34, "entityId=i32;entityStatus=i8");
        layout(774, "PLAY", false, 43, "keepAliveId=i64");
        layout(774, "PLAY", false, 55, "x=f64;y=f64;z=f64;yaw=f32;pitch=f32");
        layout(774, "PLAY", false, 62, "flags=i8;flyingSpeed=f32;walkingSpeed=f32");
        layout(774, "PLAY", false, 65, "");
        layout(774, "PLAY", false, 70, "teleportId=varint;x=f64;y=f64;z=f64;dx=f64;dy=f64;dz=f64;yaw=f32;pitch=f32;flags=position_flags");
        layout(774, "PLAY", false, 71, "yaw=f32;relativeYaw=bool;pitch=f32;relativePitch=bool");
        layout(774, "PLAY", false, 99, "entityId=varint;velocity=unsupported");
        layout(774, "PLAY", false, 102, "health=f32;food=varint;foodSaturation=f32");
        layout(774, "PLAY", false, 103, "slot=varint");
        layout(774, "PLAY", false, 116, "");
        // https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/26.1/protocol.json
        // SHA-256: 0bb4f020fa45cc6629748f09546ec2bfd645f1843cccac9da2cab9d0539ab7e3
        VERSIONS.put(775, "26.1");
        names(775, "HANDSHAKING", true, "0=set_protocol;fe=legacy_server_list_ping");
        names(775, "HANDSHAKING", false, "");
        names(775, "STATUS", true, "0=ping_start;1=ping");
        layout(775, "STATUS", true, 0, "");
        names(775, "STATUS", false, "0=server_info;1=ping");
        names(775, "LOGIN", true, "0=login_start;1=encryption_begin;2=login_plugin_response;3=login_acknowledged;4=cookie_response");
        layout(775, "LOGIN", true, 0, "username=string;playerUUID=UUID");
        layout(775, "LOGIN", true, 3, "");
        names(775, "LOGIN", false, "0=disconnect;1=encryption_begin;2=success;3=compress;4=login_plugin_request;5=cookie_request");
        layout(775, "LOGIN", false, 2, "uuid=UUID;username=string;properties=unsupported");
        layout(775, "LOGIN", false, 3, "threshold=varint");
        names(775, "CONFIGURATION", true, "0=settings;1=cookie_response;2=custom_payload;3=finish_configuration;4=keep_alive;5=pong;6=resource_pack_receive;7=select_known_packs;8=custom_click_action;9=accept_code_of_conduct");
        layout(775, "CONFIGURATION", true, 3, "");
        layout(775, "CONFIGURATION", true, 4, "keepAliveId=i64");
        layout(775, "CONFIGURATION", true, 9, "");
        names(775, "CONFIGURATION", false, "0=cookie_request;1=custom_payload;2=disconnect;3=finish_configuration;4=keep_alive;5=ping;6=reset_chat;7=registry_data;8=remove_resource_pack;9=add_resource_pack;a=store_cookie;b=transfer;c=feature_flags;d=tags;e=select_known_packs;f=custom_report_details;10=server_links;11=clear_dialog;12=show_dialog;13=code_of_conduct");
        layout(775, "CONFIGURATION", false, 3, "");
        layout(775, "CONFIGURATION", false, 4, "keepAliveId=i64");
        layout(775, "CONFIGURATION", false, 6, "");
        names(775, "PLAY", true, "0=teleport_confirm;1=attack;2=query_block_nbt;3=select_bundle_item;4=set_difficulty;5=change_gamemode;6=message_acknowledgement;7=chat_command;8=chat_command_signed;9=chat_message;a=chat_session_update;b=chunk_batch_received;c=client_command;d=tick_end;e=settings;f=tab_complete;10=configuration_acknowledged;11=enchant_item;12=window_click;13=close_window;14=set_slot_state;15=cookie_response;16=custom_payload;17=debug_subscription_request;18=edit_book;19=query_entity_nbt;1a=use_entity;1b=generate_structure;1c=keep_alive;1d=lock_difficulty;1e=position;1f=position_look;20=look;21=flying;22=vehicle_move;23=steer_boat;24=pick_item_from_block;25=pick_item_from_entity;26=ping_request;27=craft_recipe_request;28=abilities;29=block_dig;2a=entity_action;2b=player_input;2c=player_loaded;2d=pong;2e=recipe_book;2f=displayed_recipe;30=name_item;31=resource_pack_receive;32=advancement_tab;33=select_trade;34=set_beacon_effect;35=held_item_slot;36=update_command_block;37=update_command_block_minecart;38=set_creative_slot;39=set_game_rule;3a=update_jigsaw_block;3b=update_structure_block;3c=set_test_block;3d=update_sign;3e=spectate_entity;3f=arm_animation;40=spectate;41=test_instance_block_action;42=block_place;43=use_item;44=custom_click_action");
        layout(775, "PLAY", true, 0, "teleportId=varint");
        layout(775, "PLAY", true, 1, "entityId=varint");
        layout(775, "PLAY", true, 7, "command=string");
        layout(775, "PLAY", true, 8, "command=string;timestamp=i64;salt=i64;argumentSignatures=unsupported;messageCount=varint;acknowledged=unsupported;checksum=u8");
        layout(775, "PLAY", true, 9, "message=string;timestamp=i64;salt=i64;signature=unsupported;offset=varint;acknowledged=unsupported;checksum=u8");
        layout(775, "PLAY", true, 12, "actionId=varint");
        layout(775, "PLAY", true, 13, "");
        layout(775, "PLAY", true, 16, "");
        layout(775, "PLAY", true, 19, "windowId=varint");
        layout(775, "PLAY", true, 26, "target=varint;hand=varint;location=unsupported;sneaking=bool");
        layout(775, "PLAY", true, 28, "keepAliveId=i64");
        layout(775, "PLAY", true, 30, "x=f64;y=f64;z=f64;flags=movement_flags");
        layout(775, "PLAY", true, 31, "x=f64;y=f64;z=f64;yaw=f32;pitch=f32;flags=movement_flags");
        layout(775, "PLAY", true, 32, "yaw=f32;pitch=f32;flags=movement_flags");
        layout(775, "PLAY", true, 33, "flags=movement_flags");
        layout(775, "PLAY", true, 34, "x=f64;y=f64;z=f64;yaw=f32;pitch=f32;onGround=bool");
        layout(775, "PLAY", true, 35, "leftPaddle=bool;rightPaddle=bool");
        layout(775, "PLAY", true, 40, "flags=i8");
        layout(775, "PLAY", true, 41, "status=varint;location=position;face=i8;sequence=varint");
        layout(775, "PLAY", true, 42, "entityId=varint;actionId=varint;jumpBoost=varint");
        layout(775, "PLAY", true, 43, "inputs=inputs");
        layout(775, "PLAY", true, 44, "");
        layout(775, "PLAY", true, 53, "slotId=i16");
        layout(775, "PLAY", true, 63, "hand=varint");
        layout(775, "PLAY", true, 66, "hand=varint;location=position;direction=varint;cursorX=f32;cursorY=f32;cursorZ=f32;insideBlock=bool;worldBorderHit=bool;sequence=varint");
        layout(775, "PLAY", true, 67, "hand=varint;sequence=varint;rotation=vec2f");
        names(775, "PLAY", false, "0=bundle_delimiter;1=spawn_entity;2=animation;3=statistics;4=acknowledge_player_digging;5=block_break_animation;6=tile_entity_data;7=block_action;8=block_change;9=boss_bar;a=difficulty;b=chunk_batch_finished;c=chunk_batch_start;d=chunk_biomes;e=clear_titles;f=tab_complete;10=declare_commands;11=close_window;12=window_items;13=craft_progress_bar;14=set_slot;15=cookie_request;16=set_cooldown;17=chat_suggestions;18=custom_payload;19=damage_event;1a=debug_block_value;1b=debug_chunk_value;1c=debug_entity_value;1d=debug_event;1e=debug_sample;1f=hide_message;20=kick_disconnect;21=profileless_chat;22=entity_status;23=sync_entity_position;24=explosion;25=unload_chunk;26=game_state_change;27=game_rule_values;28=game_test_highlight_pos;29=open_horse_window;2a=hurt_animation;2b=initialize_world_border;2c=keep_alive;2d=map_chunk;2e=world_event;2f=world_particles;30=update_light;31=login;32=low_disk_space_warning;33=map;34=trade_list;35=rel_entity_move;36=entity_move_look;37=move_minecart;38=entity_look;39=vehicle_move;3a=open_book;3b=open_window;3c=open_sign_entity;3d=ping;3e=ping_response;3f=craft_recipe_response;40=abilities;41=player_chat;42=end_combat_event;43=enter_combat_event;44=death_combat_event;45=player_remove;46=player_info;47=face_player;48=position;49=player_rotation;4a=recipe_book_add;4b=recipe_book_remove;4c=recipe_book_settings;4d=entity_destroy;4e=remove_entity_effect;4f=reset_score;50=remove_resource_pack;51=add_resource_pack;52=respawn;53=entity_head_rotation;54=multi_block_change;55=select_advancement_tab;56=server_data;57=action_bar;58=world_border_center;59=world_border_lerp_size;5a=world_border_size;5b=world_border_warning_delay;5c=world_border_warning_reach;5d=camera;5e=update_view_position;5f=update_view_distance;60=set_cursor_item;61=spawn_position;62=scoreboard_display_objective;63=entity_metadata;64=attach_entity;65=entity_velocity;66=entity_equipment;67=experience;68=update_health;69=held_item_slot;6a=scoreboard_objective;6b=set_passengers;6c=set_player_inventory;6d=teams;6e=scoreboard_score;6f=simulation_distance;70=set_title_subtitle;71=update_time;72=set_title_text;73=set_title_time;74=entity_sound_effect;75=sound_effect;76=start_configuration;77=stop_sound;78=store_cookie;79=system_chat;7a=playerlist_header;7b=nbt_query_response;7c=collect;7d=entity_teleport;7e=test_instance_block_status;7f=set_ticking_state;80=step_tick;81=transfer;82=advancements;83=entity_update_attributes;84=entity_effect;85=declare_recipes;86=tags;87=set_projectile_power;88=custom_report_details;89=server_links;8a=tracked_waypoint;8b=clear_dialog;8c=show_dialog");
        layout(775, "PLAY", false, 12, "");
        layout(775, "PLAY", false, 17, "windowId=varint");
        layout(775, "PLAY", false, 34, "entityId=i32;entityStatus=i8");
        layout(775, "PLAY", false, 44, "keepAliveId=i64");
        layout(775, "PLAY", false, 50, "");
        layout(775, "PLAY", false, 57, "x=f64;y=f64;z=f64;yaw=f32;pitch=f32");
        layout(775, "PLAY", false, 64, "flags=i8;flyingSpeed=f32;walkingSpeed=f32");
        layout(775, "PLAY", false, 67, "");
        layout(775, "PLAY", false, 72, "teleportId=varint;x=f64;y=f64;z=f64;dx=f64;dy=f64;dz=f64;yaw=f32;pitch=f32;flags=position_flags");
        layout(775, "PLAY", false, 73, "yaw=f32;relativeYaw=bool;pitch=f32;relativePitch=bool");
        layout(775, "PLAY", false, 101, "entityId=varint;velocity=unsupported");
        layout(775, "PLAY", false, 104, "health=f32;food=varint;foodSaturation=f32");
        layout(775, "PLAY", false, 105, "slot=varint");
        layout(775, "PLAY", false, 118, "");
        // The entity ID prefix is identical in all bundled schemas. Complex world data stays raw.
        for (int protocol : VERSIONS.keySet()) {
            Integer id = packetId(protocol, "PLAY", false, "login");
            if (id != null) layout(protocol, "PLAY", false, id, "entityId=i32;isHardcore=bool;worldData=unsupported");
        }
    }

    private ProtocolDecoder() { }

    public static boolean supports(int protocol) { return VERSIONS.containsKey(protocol); }
    public static String versionName(int protocol) { return VERSIONS.getOrDefault(protocol, "unknown (" + protocol + ")"); }

    public static String packetName(int protocol, String phase, boolean serverbound, int id) {
        return NAMES.getOrDefault(key(protocol, phase, serverbound, id), String.format(Locale.ROOT, "unknown_0x%02x", id));
    }

    public static Integer packetId(int protocol, String phase, boolean serverbound, String name) {
        String prefix = protocol + "|" + normalize(phase) + "|" + serverbound + "|";
        for (Map.Entry<String, String> entry : NAMES.entrySet()) {
            if (entry.getKey().startsWith(prefix) && entry.getValue().equals(name)) {
                return Integer.valueOf(entry.getKey().substring(prefix.length()));
            }
        }
        return null;
    }

    /** packetData is the uncompressed packet, including its VarInt packet ID. */
    public static Map<String, Object> fields(int protocol, String phase, boolean serverbound, int id,
                                              byte[] packetData) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(packetData));
        if (varInt(in) != id) throw new IOException("Packet ID does not match decoded frame");
        String name = packetName(protocol, phase, serverbound, id);
        Map<String, Object> result = map();
        String layout = LAYOUTS.get(key(protocol, phase, serverbound, id));
        boolean completeLayout = layout != null;
        if (("disconnect".equals(name) || "kick_disconnect".equals(name))
                && ("LOGIN".equals(normalize(phase)) || protocol == 754)) {
            result.put("reason", string(in));
            completeLayout = true;
        } else if ("use_entity".equals(name) && serverbound && protocol < 775) {
            result.put("target", varInt(in));
            int mouse = varInt(in);
            result.put("mouse", mouse);
            result.put("action", mouse == 0 ? "interact" : mouse == 1 ? "attack" : mouse == 2 ? "interact_at" : "unknown");
            if (mouse < 0 || mouse > 2) throw new IOException("Unknown entity interaction type " + mouse);
            if (mouse == 2) {
                result.put("x", finite(in.readFloat()));
                result.put("y", finite(in.readFloat()));
                result.put("z", finite(in.readFloat()));
            }
            if (mouse != 1) result.put("hand", varInt(in));
            result.put("sneaking", bool(in));
            completeLayout = true;
        } else if ("success".equals(name) && "LOGIN".equals(normalize(phase))) {
            result.put("uuid", uuid(in));
            result.put("username", string(in));
            if (protocol != 754) {
                int count = bounded(varInt(in), 1024, "profile properties");
                List<Object> properties = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    Map<String, Object> property = map("name", string(in), "value", string(in));
                    if (bool(in)) property.put("signature", string(in));
                    properties.add(property);
                }
                result.put("properties", properties);
            }
            completeLayout = true;
        } else if (layout != null) {
            if (!layout.isEmpty()) for (String field : layout.split(";")) {
                int separator = field.indexOf('=');
                String fieldName = field.substring(0, separator);
                String type = field.substring(separator + 1);
                if ("unsupported".equals(type)) { completeLayout = false; break; }
                Object value = read(in, type);
                result.put(fieldName, value);
            }
        }
        if ("entity_action".equals(name) && result.get("actionId") instanceof Number) {
            int action = ((Number) result.get("actionId")).intValue();
            String[] actions = protocol >= 771
                    ? new String[]{"stop_sleeping", "start_sprinting", "stop_sprinting", "start_riding_jump", "stop_riding_jump", "open_inventory", "start_fall_flying"}
                    : new String[]{"start_sneaking", "stop_sneaking", "stop_sleeping", "start_sprinting", "stop_sprinting", "start_riding_jump", "stop_riding_jump", "open_inventory", "start_fall_flying"};
            result.put("action", action >= 0 && action < actions.length ? actions[action] : "unknown");
        }
        if ("attack".equals(name) && serverbound && result.containsKey("entityId")) result.put("action", "attack");
        if (result.get("flags") instanceof Map<?, ?> flags) {
            if (flags.containsKey("onGround")) result.put("onGround", flags.get("onGround"));
            if (flags.containsKey("hasHorizontalCollision")) result.put("horizontalCollision", flags.get("hasHorizontalCollision"));
        }
        result.put("decodedComplete", completeLayout && in.available() == 0);
        if (in.available() != 0) result.put("unparsedBytes", in.available());
        return result;
    }

    private static Object read(DataInputStream in, String type) throws IOException {
        switch (type) {
            case "varint": return varInt(in);
            case "varlong": return varLong(in);
            case "i8": return in.readByte();
            case "u8": return in.readUnsignedByte();
            case "i16": return in.readShort();
            case "u16": return in.readUnsignedShort();
            case "i32": return in.readInt();
            case "u32": return Integer.toUnsignedLong(in.readInt());
            case "i64": return in.readLong();
            case "f32": return finite(in.readFloat());
            case "f64": return finite(in.readDouble());
            case "bool": return bool(in);
            case "string": return string(in);
            case "UUID": return uuid(in);
            case "position": {
                long packed = in.readLong();
                return map("x", packed >> 38, "y", packed << 52 >> 52, "z", packed << 26 >> 38);
            }
            case "vec2f": return map("x", finite(in.readFloat()), "y", finite(in.readFloat()));
            case "vec3i16": return map("x", in.readShort(), "y", in.readShort(), "z", in.readShort());
            case "movement_flags": return bits(in.readUnsignedByte(), new String[]{"onGround", "hasHorizontalCollision"});
            case "position_flags": return bits(Integer.toUnsignedLong(in.readInt()), new String[]{"x", "y", "z", "yaw", "pitch", "dx", "dy", "dz", "yawDelta"});
            case "inputs": return bits(in.readUnsignedByte(), new String[]{"forward", "backward", "left", "right", "jump", "shift", "sprint"});
            default: throw new IOException("Unsupported semantic field type " + type);
        }
    }

    private static Map<String, Object> bits(long raw, String[] names) {
        Map<String, Object> result = map("raw", raw);
        for (int i = 0; i < names.length; i++) result.put(names[i], (raw & (1L << i)) != 0);
        return result;
    }

    private static boolean bool(DataInputStream in) throws IOException {
        int value = in.readUnsignedByte();
        if (value > 1) throw new IOException("Invalid Boolean value " + value);
        return value != 0;
    }

    private static double finite(double value) throws IOException {
        if (!Double.isFinite(value)) throw new IOException("Non-finite coordinate or rotation");
        return value;
    }

    private static String uuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong()).toString();
    }

    private static int bounded(int value, int max, String what) throws IOException {
        if (value < 0 || value > max) throw new IOException("Invalid " + what + " length " + value);
        return value;
    }

    private static String string(DataInputStream in) throws IOException {
        int length = bounded(varInt(in), 131068, "string");
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int varInt(DataInputStream in) throws IOException {
        int value = 0;
        for (int i = 0; i < 5; i++) {
            int b = in.readUnsignedByte();
            if (i == 4 && (b & 0xf0) != 0) throw new IOException("VarInt exceeds 32 bits");
            value |= (b & 0x7f) << (i * 7);
            if ((b & 0x80) == 0) return value;
        }
        throw new IOException("VarInt too long");
    }

    private static long varLong(DataInputStream in) throws IOException {
        long value = 0;
        for (int i = 0; i < 10; i++) {
            int b = in.readUnsignedByte();
            if (i == 9 && (b & 0xfe) != 0) throw new IOException("VarLong exceeds 64 bits");
            value |= (long) (b & 0x7f) << (i * 7);
            if ((b & 0x80) == 0) return value;
        }
        throw new IOException("VarLong too long");
    }

    private static String normalize(String phase) {
        String value = phase.toUpperCase(Locale.ROOT);
        return value.equals("HANDSHAKE") || value.equals("HANDSHAKING") ? "HANDSHAKING" : value.equals("CONFIG") ? "CONFIGURATION" : value;
    }

    private static String key(int protocol, String phase, boolean serverbound, int id) {
        return protocol + "|" + normalize(phase) + "|" + serverbound + "|" + id;
    }

    private static void names(int protocol, String phase, boolean serverbound, String entries) {
        if (entries.isEmpty()) return;
        for (String entry : entries.split(";")) {
            int separator = entry.indexOf('=');
            NAMES.put(key(protocol, phase, serverbound, Integer.parseInt(entry.substring(0, separator), 16)), entry.substring(separator + 1));
        }
    }

    private static void layout(int protocol, String phase, boolean serverbound, int id, String fields) {
        LAYOUTS.put(key(protocol, phase, serverbound, id), fields);
    }

    private static Map<String, Object> map(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) result.put((String) entries[i], entries[i + 1]);
        return result;
    }
}
