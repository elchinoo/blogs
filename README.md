# Blogs and posts

- PostgreSQL PL/Java - How to install and use Java inside Postgres
  - [Part 1 - Introduction and installation](https://github.com/elchinoo/blogs/tree/main/pljava/part1)
  - [Part 2 - How to manipulate and return tuples in PL/Java](https://github.com/elchinoo/blogs/tree/main/pljava/part2)
  - Part 3 - How to access and use externel resources

## pandoc

You can use [**pandoc**[1]](https://pandoc.org/) to export the Markdown documents here to other formats, for example:

```bash
## Exporting PL/Java Part 2 post to ODT format:
pandoc pljava/part2/README.md -f markdown -t odt -s -o pljava-part2.odt

## Exporting PL/Java Part 2 post to docx format:
pandoc pljava/part2/README.md -f markdown -t docx -s -o pljava-part2.docx

## Exporting PL/Java Part 2 post to PDF format:
pandoc pljava/part2/README.md -f markdown -t pdf -s -o pljava-part2.pdf
```

<br />
[1] https://pandoc.org/