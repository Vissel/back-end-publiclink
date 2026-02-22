CREATE TABLE IF NOT EXISTS sale_environment(
	env_id varchar(50) primary key,
    req_id bigint not null,
    public_link varchar(255) not null,
    state tinyint default 1,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP,
    ended_at timestamp,
    foreign key (req_id) references request(req_id)
);

alter table product
add column req_id bigint not null;
alter table product
add foreign key (req_id ) references request(req_id);

CREATE TABLE IF NOT EXISTS payment_method(
	method_id int primary key auto_increment,
    method_name varchar(50),
    method_info varchar(50)
);

CREATE TABLE IF NOT EXISTS `order`(
	order_id bigint primary key auto_increment,
    buyer_name varchar(50) not null,
    env_id varchar(50) not null,
    ordered_at timestamp DEFAULT CURRENT_TIMESTAMP,
    method_id int null,
    delivered tinyint default 0,
    get_money tinyint default 0,
    
    foreign key (env_id) references sale_environment(env_id),
    foreign key (method_id) references payment_method(method_id)
);

