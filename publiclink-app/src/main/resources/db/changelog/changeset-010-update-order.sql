alter table `order`
add column amount int default 1,
add column unit varchar(50) default null,
add column note varchar(250) default null;