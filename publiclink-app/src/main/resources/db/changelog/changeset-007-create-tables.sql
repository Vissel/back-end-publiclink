CREATE TABLE IF NOT EXISTS payment_method(
	method_id int primary key,
    method_name varchar(50),
    method_info varchar(50)
);

CREATE TABLE IF NOT EXISTS `order`(
	order_id bigint primary key ,
    buyer_name varchar(250) not null,
    env_id varchar(50) not null,
    ordered_at timestamp DEFAULT CURRENT_TIMESTAMP,
    seller_note varchar(250) null,
    delivered tinyint default 0,
    get_money tinyint default 0,
    
    foreign key (env_id) references sale_environment(env_id),
    foreign key (method_id) references payment_method(method_id)
);
