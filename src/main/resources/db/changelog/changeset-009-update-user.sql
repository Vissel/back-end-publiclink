alter table `user`
ADD CONSTRAINT unique_usernamelink UNIQUE (user_name ,link);