-- US5: si cada snippet cumple las reglas de linteo. Se calcula al crear o modificar el snippet,
-- asi el listado puede filtrar y ordenar por esto sin lintear todo en cada pedido.
-- Los snippets que ya existian quedan PENDING: todavia nadie los linteo
alter table snippet add column lint_status varchar(20) default 'PENDING' not null;
