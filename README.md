# Meteor Valley Builder — Minecraft 26.1.2

This branch targets **Minecraft 26.1.2**, **Meteor Client 26.1.2** and **Java 25**.

## Download the JAR

1. Open [GitHub Actions](https://github.com/habue/meteor-valley-builder/actions/workflows/dev_build.yml).
2. Select a successful **Build JAR** run for branch `meteor-26.1.2`.
3. Download **valley-builder-mc26.1.2** from the run's **Artifacts** section and extract the JAR.
4. Put the JAR in `.minecraft/mods` alongside Meteor Client for 26.1.2.

Every push and pull request builds an installable JAR. You can also start a build manually with **Run workflow**.

## Build locally

Install JDK 25, then run `bash ./gradlew clean build`. The installable JAR is in `build/libs/`.

<div align="center">
  <!-- Logo and Title -->
  <img src="/src/main/resources/assets/template/icon.png" alt="logo" width="20%"/>
  <h1>
    Meteor Valley Builder<br>
    <sub>by dinosaurwizard</sub>
  </h1>

  <p>An addon for easily building 'valleys' in minecraft.</p>
</div>

<hr />

# Showcase

https://github.com/user-attachments/assets/b4881994-c5c3-4fce-a9c6-4d068f699801

# How To Use

Simply activate the module `Valley Builder` and configure it to your server's needs.<br>

If Baritone API is available, this addon also includes the module `Valley Builder Auto` for automatic building of valleys.

#### Some activities valley builder is capable of:
- Seeding large farms of wheat or other crops.
- Covering an entire desert in cactus.
- Planting a huge field of flowers.
- General purpose building (e.g creating a randomly textured wall).
- Assistance with making mapart.


# Installation
- Download the relevant [release](/../../releases) of the mod from the releases tab.
- Put the .jar in your `.minecraft/mods` folder where you have installed Meteor.
- This addon requires [Meteor Client](https://meteorclient.com/) as a dependency.
- This addon optionally requires [Baritone API](https://github.com/cabaletta/baritone) as a dependency.



