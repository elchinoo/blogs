# PostgreSQL PL/Java - How to install and use Java inside Postgres

This is a series of 3 posts about PL/Java and intends to demonstrate how to install it to PostgreSQL compiling from the sources. As the posts advance they'll demonstrate how to manipulate database objects, like tables, from inside the Java objects and eventually return them. I'm trying to create examples more interesting than the old "Hello world". We will, for example, show how to use hash and cryptographic functions in the Part 2 as part of the examples on how to manipulate and return objects from Java. I hope you find it useful and please submit any bug, error, suggestion or comment you might have!

- [Part 1 - Introduction and installation](https://github.com/elchinoo/blogs/tree/main/pljava/part1)
- [Part 2 - How to manipulate and return tuples in PL/Java](https://github.com/elchinoo/blogs/tree/main/pljava/part2)
- Part 3 - How to access and use externel resources.

Note that the codes from each part are in the "**src**" folder inside its own article. You can also find the "**pagila-0.10.1.zip**" sample database I'm using in this series of articles in the folder [**dbsamples**[1] here](https://github.com/elchinoo/blogs/tree/main/dbsamples) or download from [pgFoundry[2]](https://www.postgresql.org/ftp/projects/pgFoundry/dbsamples/pagila/).

<br />
[1] https://github.com/elchinoo/blogs/tree/main/dbsamples <br />
[2] https://www.postgresql.org/ftp/projects/pgFoundry/dbsamples/pagila/