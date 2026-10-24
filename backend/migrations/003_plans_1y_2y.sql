-- The product sells 1-year and 2-year access. 2 devices per key by default (changeable per key at generation).
insert into vip_plans (id, name, duration_days, device_limit) values
  ('vip_1y', 'VIP 1 an', 365, 2),
  ('vip_2y', 'VIP 2 ans', 730, 2);

-- Drop the starter plans, unless a key or entitlement already uses them.
delete from vip_plans p
where p.id in ('vip_30d', 'vip_90d', 'vip_365d')
  and not exists (select 1 from vip_keys k where k.plan_id = p.id)
  and not exists (select 1 from vip_entitlements e where e.plan_id = p.id);
