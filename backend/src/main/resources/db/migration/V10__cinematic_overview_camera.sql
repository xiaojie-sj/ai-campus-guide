-- Cinematic overview: lower pitch so the brighter sky and cloud layer stay visible
-- behind the campus instead of filling the frame with ground.
UPDATE camera_preset
SET x = 48.0,
    y = 22.0,
    z = 74.0,
    target_x = 0.0,
    target_y = 3.0,
    target_z = -2.0
WHERE preset_key = 'overview';
