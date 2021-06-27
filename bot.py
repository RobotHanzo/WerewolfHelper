import json
import logging

import discord
from discord.ext import commands
from discord_slash import SlashCommand

from utils.voting import Voting

intents = discord.Intents.all()
bot = commands.Bot(command_prefix='w!', case_insensitive=False, intents=intents,
                   activity=discord.Game(name='Made by Hanzo'), status=discord.Status.online)
bot.slash = SlashCommand(bot, sync_commands=True, sync_on_cog_reload=True)
bot.voting = Voting(bot)
logging.basicConfig(level=logging.INFO)
with open('cred.json', 'r') as f:
    creds = f.read()
    creds = json.loads(creds)

cogs = ['cogs.create', 'cogs.ready', 'cogs.gameplay', 'jishaku']
for cog in cogs:
    bot.load_extension(cog)


@bot.event
async def on_ready():
    print('Ready.')


bot.run(creds['token'])
