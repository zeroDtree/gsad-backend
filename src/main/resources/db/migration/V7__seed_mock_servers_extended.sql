-- Seed gpu-mock-004 .. gpu-mock-030 for dev/demo (001-003 from V6).
INSERT INTO t_server (
    hostname, server_id, ssh_host, resource_level, status,
    last_reported_at, avg_util, avg_mem_used_mb, metrics_json
)
SELECT
    format('gpu-mock-%s.internal', lpad(i::text, 3, '0')),
    format('gpu-mock-%s', lpad(i::text, 3, '0')),
    format('10.0.%s.%s', ((i / 250) + 1), ((i % 250) + 1)),
    (ARRAY['H100', 'A100', 'L40S', 'L4', 'T4'])[1 + ((i - 1) % 5)],
    'ONLINE',
    NOW(),
    ROUND((0.18 + ((i - 1) % 8) * 0.10)::numeric, 4),
    CASE (i - 1) % 5
        WHEN 0 THEN 63936
        WHEN 1 THEN 36864
        WHEN 2 THEN 10803
        WHEN 3 THEN 4423
        WHEN 4 THEN 2949
    END,
    jsonb_build_object(
        'collectedAt', to_char(NOW() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
        'summary', jsonb_build_object(
            'gpuCount', (ARRAY[8, 4, 2, 1, 1])[1 + ((i - 1) % 5)],
            'avgUtil', ROUND((0.18 + ((i - 1) % 8) * 0.10)::numeric, 2),
            'avgMemUsedMb', CASE (i - 1) % 5
                WHEN 0 THEN 63936 WHEN 1 THEN 36864 WHEN 2 THEN 10803
                WHEN 3 THEN 4423 ELSE 2949 END
        ),
        'gpus', jsonb_build_array(jsonb_build_object(
            'index', 0,
            'name', (ARRAY['NVIDIA H100', 'NVIDIA A100', 'NVIDIA L40S', 'NVIDIA L4', 'NVIDIA T4'])[1 + ((i - 1) % 5)],
            'avgUtil', ROUND((0.18 + ((i - 1) % 8) * 0.10)::numeric, 2),
            'memUsedMb', CASE (i - 1) % 5
                WHEN 0 THEN 63936 WHEN 1 THEN 36864 WHEN 2 THEN 10803
                WHEN 3 THEN 4423 ELSE 2949 END,
            'memTotalMb', (ARRAY[81920, 81920, 49152, 24576, 16384])[1 + ((i - 1) % 5)]
        ))
    )
FROM generate_series(4, 30) AS i
WHERE NOT EXISTS (
    SELECT 1 FROM t_server WHERE server_id = format('gpu-mock-%s', lpad(i::text, 3, '0'))
);
