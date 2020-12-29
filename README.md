# nand2tetris

This repo is a collection of software projects from the open-source nand2tetris course. The course consists of two parts:

- building a 16-bit computer, starting from nand gates (we design an ALU, CPU, RAM module, and all the chips that compose these units) - this is done using a custom flavor of VHDL/Verilog

- building a software platform for the aforementioned computer; this consists of an assembler, virtual machine, compiler (for a high-level language unique to our computer), and an operating system

# Descriptions of the software projects:

Assembler: translates machine language from its (human-readable) symbolic representation to its binary representation, consuming assembly files and producing binary files

- it is based on the specification/grammar provided in the course (the spec is based on the 16-bit computer we built in the first half of the course)

- first, we remove all comments and whitespace from the file. Next, we do an initial pass over the set of instructions, enumerating all goto labels (pseudo-instructions). Finally, we parse and map the remaining instructions one-by-one

- we decompose computation instructions into fields and convert these to binary individually. Additionally, we map symbols/variables (called address instructions) to addresses that point to instruction/data registers. Note that we dynamically allocate memory for variables
